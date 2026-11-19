package com.fundmessenger.fund.dto;

import com.fundmessenger.fund.entity.PlanSnapshot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class PlanSnapshotResponse {

    private Long id;
    private Long positionId;
    private Long savedById;
    private String savedByName;
    private List<Map<String, Object>> buyPlan;
    private List<Map<String, Object>> takeProfitTargets;
    private List<Map<String, Object>> stopLossTargets;
    private String memo;
    private int version;
    private LocalDateTime createdAt;

    public static PlanSnapshotResponse from(PlanSnapshot snapshot) {
        return PlanSnapshotResponse.builder()
                .id(snapshot.getId())
                .positionId(snapshot.getPosition().getId())
                .savedById(snapshot.getSavedById())
                .savedByName(snapshot.getSavedByName())
                .buyPlan(snapshot.getBuyPlan())
                .takeProfitTargets(snapshot.getTakeProfitTargets())
                .stopLossTargets(snapshot.getStopLossTargets())
                .memo(snapshot.getMemo())
                .version(snapshot.getVersion())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }
}
