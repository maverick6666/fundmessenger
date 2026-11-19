---
name: deployment-guide
description: 프로덕션 배포 전체 가이드 — GCP Cloud Run + Cloudflare Pages, 인프라/설정/운영/트러블슈팅
type: reference
---

# 펀드메신저 프로덕션 배포 가이드

## 1. 인프라 구성

| 구성 요소 | 서비스 | 주소/설정 |
|-----------|--------|-----------|
| 프론트엔드 | Cloudflare Pages | `https://<WEB_DOMAIN>` |
| 백엔드 API | GCP Cloud Run | `https://<API_DOMAIN>` (Worker 프록시) |
| Cloud Run 직접 | GCP Cloud Run | `https://<CLOUD_RUN_SERVICE_URL>` |
| DB | Cloud SQL PostgreSQL 18 | `<GCP_PROJECT_ID>:asia-northeast3:fundmessenger-db` |
| 파일 저장 | Cloud Storage | 버킷 `<GCS_BUCKET_NAME>` (서울, 공개 읽기) |
| Docker Registry | Artifact Registry | `asia-northeast3-docker.pkg.dev/<GCP_PROJECT_ID>/fundmessenger` |
| API 프록시 | Cloudflare Worker | `api-proxy` (Host Header 변환) |
| 도메인 | Cloudflare Registrar | `<WEB_DOMAIN>` |

### GCP 프로젝트
- ID: `<GCP_PROJECT_ID>`
- 리전: `asia-northeast3` (서울)
- 무료 크레딧: ~42만원 (90일)

### Cloud Run 설정
- CPU: 2, 메모리: 8Gi (CPU 2개에 최대 8Gi)
- min/max instances: 1 (단일 인스턴스 — 인메모리 상태 보호)
- cpu-always-allocated (스케줄러/비동기 작업용)
- timeout: 3600초

### 서울 리전 제약
- Cloud Run 도메인 매핑 미지원 → Cloudflare Worker로 우회
- Worker 코드: `<API_DOMAIN>` → `.run.app`으로 프록시 전달

## 2. 배포 전 코드 변경 사항

### FileStorageService GCS 듀얼모드
- `GCS_BUCKET_NAME` 환경변수 비면 로컬 모드, 있으면 GCS 모드
- `getPublicUrl()`: GCS면 `https://storage.googleapis.com/bucket/name`, 로컬이면 `/api/files/images/name`
- `downloadAsBytes()`: GCS/로컬 모두 지원
- `getUploadDir()`: GCS 모드에서는 UnsupportedOperationException
- 호출자 수정: DriveService, AiGenerationService, PdfToImageService

### DriveFileResponse/PostDetailResponse
- `fileUrl` 필드 추가: 백엔드가 GCS/로컬 URL을 직접 반환
- 프론트엔드: `file.fileUrl` 우선 사용, 없으면 `BASE_URL/uploads/` 폴백

### OAuth state 검증
- 쿠키 기반 → 인메모리 ConcurrentHashMap (Cloudflare Worker 프록시에서 쿠키 전달 안 됨)
- AuthController.java: stateStore에 state→만료시간 저장, 콜백 시 검증+삭제

### 동아리 생성 시 자동 초기화
- AdminService.initializeClubDefaults(): 기본 게시판 2개 + 세미카테고리 + 태그 정의

### 최초 가입자 슈퍼어드민
- AuthService.createUser(): `userRepository.count() == 0`이면 `superAdmin=true`

## 3. 환경변수 (Cloud Run)

### 필수
```
ENVIRONMENT=production
SECRET_KEY=(강한 랜덤 키)
DATABASE_URL=jdbc:postgresql:///fundmessenger?cloudSqlInstance=<GCP_PROJECT_ID>:asia-northeast3:fundmessenger-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory
DB_USERNAME=funduser
DB_PASSWORD=(Cloud SQL 비밀번호)
GCS_BUCKET_NAME=<GCS_BUCKET_NAME>
FRONTEND_URL=https://<WEB_DOMAIN>
OAUTH_GOOGLE_REDIRECT=https://<API_DOMAIN>/api/auth/oauth2/callback/google
OAUTH_KAKAO_REDIRECT=https://<API_DOMAIN>/api/auth/oauth2/callback/kakao
OAUTH_NAVER_REDIRECT=https://<API_DOMAIN>/api/auth/oauth2/callback/naver
GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET
KAKAO_CLIENT_ID / KAKAO_CLIENT_SECRET
NAVER_OAUTH_CLIENT_ID / NAVER_OAUTH_CLIENT_SECRET
```

### 선택 (AI/뉴스)
```
CEREBRAS_API_KEYS, CEREBRAS_MODEL
GEMINI_API_KEYS, GEMINI_MODEL
KIS_APP_KEY, KIS_APP_SECRET
NAVER_CLIENT_ID, NAVER_CLIENT_SECRET
MARKETAUX_API_KEY
TAVILY_API_KEYS, SERPAPI_API_KEYS
VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY
NEWS_PIPELINE_ENABLED=true/false
```

### Cloudflare Pages
```
VITE_API_URL=https://<API_DOMAIN>/api
NODE_VERSION=20
```

## 4. 운영 명령어

### 백엔드 재배포 (코드 수정 후)
```bash
cd F:\fundmessenger
docker build -t asia-northeast3-docker.pkg.dev/<GCP_PROJECT_ID>/fundmessenger/backend:latest .
docker push asia-northeast3-docker.pkg.dev/<GCP_PROJECT_ID>/fundmessenger/backend:latest
gcloud run deploy fundmessenger-backend --image asia-northeast3-docker.pkg.dev/<GCP_PROJECT_ID>/fundmessenger/backend:latest --region asia-northeast3
```

### 프론트엔드 재배포
```bash
git push  # Cloudflare Pages 자동 빌드/배포 (1~2분)
```

### 환경변수 변경
```bash
gcloud run services update fundmessenger-backend --region asia-northeast3 --update-env-vars "KEY=VALUE"
```

### 환경변수 일괄 적용 (YAML)
```bash
gcloud run services update fundmessenger-backend --region asia-northeast3 --env-vars-file cloud-run-env.yaml
```

### 로그 확인
```bash
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=fundmessenger-backend AND severity>=ERROR" --limit 10 --format "value(textPayload)" --project <GCP_PROJECT_ID>
```

### 종목 마스터 수동 동기화
```bash
curl -X POST https://<API_DOMAIN>/api/admin/stocks/sync -H "Authorization: Bearer {JWT토큰}"
```

### 뉴스 파이프라인 수동 실행
```bash
curl -X POST https://<API_DOMAIN>/api/admin/news-pipeline/run -H "Authorization: Bearer {JWT토큰}"
```

## 5. 트러블슈팅 기록

### Cloud SQL 접근 403
- 원인: Cloud Run 서비스 계정에 cloudsql.client 역할 없음
- 해결: `gcloud projects add-iam-policy-binding <GCP_PROJECT_ID> --member="serviceAccount:<PROJECT_NUMBER>-compute@developer.gserviceaccount.com" --role="roles/cloudsql.client"`

### GCS 업로드 403
- 원인: Cloud Run 서비스 계정에 storage.objectAdmin 역할 없음
- 해결: `gcloud projects add-iam-policy-binding <GCP_PROJECT_ID> --member="serviceAccount:<PROJECT_NUMBER>-compute@developer.gserviceaccount.com" --role="roles/storage.objectAdmin"`

### DB 비밀번호 인증 실패
- 원인: 셸에서 `$`, `!` 특수문자가 변수/히스토리 확장으로 해석됨
- 해결: Cloud Run 콘솔에서 직접 입력하거나 특수문자 없는 비밀번호 사용

### OAuth state 쿠키 미전달
- 원인: Cloudflare Worker 프록시 환경에서 Set-Cookie가 브라우저에 도달하지만 콜백 시 Cookie 헤더가 다른 도메인으로 전달 안 됨
- 해결: 쿠키 대신 인메모리 ConcurrentHashMap으로 state 저장/검증

### 서울 리전 도메인 매핑 불가
- 원인: `asia-northeast3`에서 Cloud Run domain-mappings create 501 에러
- 해결: Cloudflare Worker로 `<API_DOMAIN>` → `.run.app` 프록시

### Cloudflare Origin Rules Host Header Rewrite 불가
- 원인: 무료 플랜에서 Host Header rewrite 미지원
- 해결: Cloudflare Worker로 동일 기능 구현

### Container failed to start (startup probe)
- 원인 1: Cloud SQL 접근 권한 없음 → IAM 역할 추가
- 원인 2: OAuth 환경변수 누락 (GOOGLE_CLIENT_ID 등) → 환경변수 추가
- 원인 3: 앱 시작 시간 초과 → --cpu-boost 옵션 추가

## 6. OAuth 상태
- Google: 동작 중 (검수 불필요, 일반 사용자 로그인 가능)
- 카카오: 검수 전 (앱 소유자만 로그인 가능, 로그인 버튼 주석 처리)
- 네이버: 검수 전 (앱 소유자만 로그인 가능, 로그인 버튼 주석 처리)
- 카카오/네이버 검수 통과 후: LoginPage.jsx에서 주석 해제하면 즉시 활성화

## 7. IAM 역할 (서비스 계정: <PROJECT_NUMBER>-compute@developer.gserviceaccount.com)
- roles/cloudsql.client (Cloud SQL 접근)
- roles/storage.objectAdmin (GCS 업로드/삭제)
