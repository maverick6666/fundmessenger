@echo off
set DATABASE_URL=jdbc:postgresql://localhost:5434/fundmessenger
set DB_USERNAME=funduser
set DB_PASSWORD=%DB_PASSWORD%
call gradlew.bat bootRun
