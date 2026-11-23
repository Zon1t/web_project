#!/usr/bin/env bash
# round23.sh — A파트 라운드 2·3: SQL로 상태를 만들어 확인하는 시나리오 (C 작성)
#   PARTY-03, PARTY-08(3), PARTY-11(4), PARTY-13, CHAT-03, THUMBS-01~04, SCHED-01·02
# 실행: bash round23.sh 2>&1 | tee round23-result.txt
# - 시작할 때 DB 비밀번호를 한 번 묻는다 (화면·파일에 남지 않음, sqlplus 표준입력으로만 전달)
# - SQL로 바꾸는 것은 testa… 계정이 만든 식사팟뿐. 정리는 cleanup.sql
# - 마지막 스케줄러 확인 때문에 최대 10분 정도 걸린다

cd "$(dirname "$0")" || exit 1
source ./lib.sh

# ---------- DB 연결 ----------
read -r -p "DB 사용자 [hr]: " DBUSER; DBUSER=${DBUSER:-hr}
read -r -p "DB 주소 [localhost:1521/XE]: " DBCONN; DBCONN=${DBCONN:-localhost:1521/XE}
read -r -s -p "DB 비밀번호: " DBPW; echo

# sql "SQL문;" → 결과 출력 (머리글 없이). DML 뒤에는 COMMIT을 같이 보낸다
PWQ='"'   # 비밀번호 감싸는 따옴표 (접속 실패하면 따옴표 없이 다시 시도)
sql() {
  printf 'CONNECT %s/%s%s%s@%s\nSET HEADING OFF FEEDBACK OFF PAGESIZE 0 VERIFY OFF ECHO OFF TRIMOUT ON TRIMSPOOL ON LINESIZE 300\nWHENEVER SQLERROR EXIT 1\n%s\nEXIT\n' \
    "$DBUSER" "$PWQ" "$DBPW" "$PWQ" "$DBCONN" "$1" | sqlplus -S /nolog | tr -d '\r' | sed '/^\s*$/d; s/^\s*//; s/\s*$//'
}
# sqldo "UPDATE ...; COMMIT;" → 오류가 있으면 화면에 보여 준다
sqldo() { local o; o=$(sql "$1"); [ -n "$o" ] && echo "  !! SQL 메시지: $o"; }
MID() { echo "(SELECT MEMBER_ID FROM MEMBER WHERE LOGIN_ID='${LOGIN[$1]}')"; }

section "준비: 서버·DB·계정"
command -v sqlplus >/dev/null || { echo "sqlplus를 찾을 수 없습니다. (Git Bash에서 'which sqlplus' 확인)"; exit 1; }
OUT=$(sql "SELECT 'DB_OK' FROM DUAL;")
if [ "$OUT" != "DB_OK" ]; then
  FIRST="$OUT"; PWQ=''; OUT=$(sql "SELECT 'DB_OK' FROM DUAL;")
fi
if [ "$OUT" != "DB_OK" ]; then
  echo "DB 접속 실패. sqlplus 응답 (비밀번호는 가림):"
  printf '%s\n%s\n' "[따옴표 O] $FIRST" "[따옴표 X] $OUT" | sed "s/$(printf '%s' "$DBPW" | sed 's/[^A-Za-z0-9]/./g')/***/g" | head -20
  exit 1
fi
info "DB 접속 확인"
for u in H M M2 N L X1 X2 X3; do
  call "$u" POST /api/auth/signup "{\"loginId\":\"${LOGIN[$u]}\",\"password\":\"$PW\",\"passwordConfirm\":\"$PW\",\"nickname\":\"${NICK[$u]}\"}"
  call "$u" POST /api/auth/login "{\"loginId\":\"${LOGIN[$u]}\",\"password\":\"$PW\"}"
  [ "$LAST_STATUS" = "200" ] || { echo "$u 로그인 실패 (서버가 8081로 켜져 있는지 확인): $LAST"; exit 1; }
done
call H GET /api/restaurants/roulette/candidates; RID=$(num restaurantId)
[ -n "$RID" ] || { echo "식당을 찾지 못했습니다: $LAST"; exit 1; }
pick_times
info "식당 ID=$RID, 끼니=$MEAL, T1=$T1, T2=$T2"

party() {  # party 정원 [시간] → PID (H가 방장)
  call H POST /api/parties/create "{\"restaurantId\":$RID,\"mealType\":\"$MEAL\",\"mealTime\":\"${2:-$T1}\",\"capacity\":$1,\"memo\":\"TEST_round23\"}"
  PID=$(num partyId); [ -n "$PID" ] || { echo "팟 생성 실패: $LAST"; exit 1; }
}
ids_in() { printf '%s' "$LAST" | grep -o '"partyId":[0-9]*' | grep -o '[0-9]*'; }
has_id() { ids_in | grep -qx "$1"; }
pass() { PASS=$((PASS+1)); echo "  PASS  $1"; }
fail() { FAIL=$((FAIL+1)); echo "  FAIL  $1"; }
check() { if eval "$2"; then pass "$1"; else fail "$1 ${3:+→ $3}"; fi; }

# ===============================================================
section "TS-A-PARTY-03 식사팟 목록 조회 (필터·정렬·제외)"
party 4 "$T2"; Q_REC=$PID
party 4 "$T1"; Q_SCH=$PID; call H PATCH /api/parties/$Q_SCH/schedule "{\"confirmedTime\":\"$T1\",\"meetPlace\":\"lobby\"}"
party 2 "$T1"; Q_FULL=$PID; call X1 POST /api/parties/$Q_FULL/members
party 4 "$T2"; Q_CLO=$PID; call H PATCH /api/parties/$Q_CLO/status '{"action":"CLOSE"}'
party 4 "$T1"; Q_FIN=$PID
party 4 "$T1"; Q_DEL=$PID; call H DELETE /api/parties/$Q_DEL
party 4 "$T1"; Q_YES=$PID
sqldo "UPDATE MEAL_PARTY SET STATUS='FINISHED' WHERE PARTY_ID=$Q_FIN; UPDATE MEAL_PARTY SET PARTY_DATE=TRUNC(SYSDATE)-1 WHERE PARTY_ID=$Q_YES; COMMIT;"
call N POST /api/parties/$Q_SCH/members   # N이 참여한 팟
info "RECRUITING=$Q_REC SCHEDULED=$Q_SCH FULL=$Q_FULL CLOSED=$Q_CLO FINISHED=$Q_FIN DELETED=$Q_DEL 어제=$Q_YES (N은 $Q_SCH 참여)"

t "1. 조건 없이 조회" N GET /api/parties/list "" 200 -
check "1. 진행 중 4개(RECRUITING·SCHEDULED·FULL·CLOSED) 포함" 'has_id $Q_REC && has_id $Q_SCH && has_id $Q_FULL && has_id $Q_CLO'
check "1. FINISHED·DELETED·어제 팟 제외" '! has_id $Q_FIN && ! has_id $Q_DEL && ! has_id $Q_YES'
TIMES=$(printf '%s' "$LAST" | grep -o '"time":"[0-9:]*"' | grep -o '[0-9][0-9]:[0-9][0-9]')
check "1. 기준 시간 오름차순" 'printf "%s\n" "$TIMES" | sort -c 2>/dev/null' "$(echo $TIMES)"
LC=$(num lunchCount); DC=$(num dinnerCount)
RL=$(printf '%s' "$LAST" | grep -o '"mealType":"LUNCH"' | wc -l); RD=$(printf '%s' "$LAST" | grep -o '"mealType":"DINNER"' | wc -l)
check "1. lunchCount·dinnerCount 정확 (응답 $LC·$DC, 목록 $RL·$RD)" '[ "$LC" = "$RL" ] && [ "$DC" = "$RD" ]'
check "1. 내가 참여한 팟 joined=true" 'printf "%s" "$LAST" | grep -q "\"partyId\":$Q_SCH,[^}]*\"joined\":true"'

t "2. mealType=$MEAL" N GET "/api/parties/list?mealType=$MEAL" "" 200 -
OTHER=$([ "$MEAL" = LUNCH ] && echo DINNER || echo LUNCH)
check "2. $MEAL만 있음" '! printf "%s" "$LAST" | grep -q "\"mealType\":\"$OTHER\"" && has_id $Q_REC'
t "3. status=FULL" N GET "/api/parties/list?status=FULL" "" 200 -
check "3. FULL만 있고 $Q_FULL 포함" '! printf "%s" "$LAST" | grep -o "\"status\":\"[A-Z]*\"" | grep -vq "\"FULL\"" && has_id $Q_FULL'
t "4. excludeJoined=true&limit=3" N GET "/api/parties/list?excludeJoined=true&limit=3" "" 200 -
CNT=$(ids_in | wc -l)
check "4. 최대 3개 ($CNT개), 내가 참여한 $Q_SCH 제외" '[ "$CNT" -le 3 ] && ! has_id $Q_SCH && ! printf "%s" "$LAST" | grep -q "\"joined\":true"'
t "5. status=FINISHED → 400" N GET "/api/parties/list?status=FINISHED" "" 400 INVALID_REQUEST

section "TS-A-PARTY-08 (3) FINISHED 팟 참여"
t "3. FINISHED 팟 참여 → PARTY_FINISHED" N POST /api/parties/$Q_FIN/members "" 409 PARTY_FINISHED

section "TS-A-PARTY-11 (4) FULL 팟 일정 확정"
t "4. FULL 팟에 일정 확정 → FULL 유지" H PATCH /api/parties/$Q_FULL/schedule "{\"confirmedTime\":\"$T1\",\"meetPlace\":\"lobby\"}" 200 - '"status":"FULL"'

# ===============================================================
section "TS-A-PARTY-13 30분 전 확정 취소·확정자 나가기 차단"
party 4; R13=$PID
call M POST /api/parties/$R13/members; call M2 POST /api/parties/$R13/members
call H PATCH /api/parties/$R13/schedule "{\"confirmedTime\":\"$T1\",\"meetPlace\":\"lobby\"}"
call M POST /api/parties/$R13/members/me/confirm
SOON=$(date -d '+20 min' +%H:%M)
sqldo "UPDATE MEAL_PARTY SET CONFIRMED_TIME='$SOON' WHERE PARTY_ID=$R13; COMMIT;"
info "R13=$R13, 확정 시간을 지금+20분($SOON)으로 변경. M 확정자, M2 미확정"
t "1. 확정자 M 확정 취소 → CANCEL_DEADLINE_PASSED" M DELETE /api/parties/$R13/members/me/confirm "" 409 CANCEL_DEADLINE_PASSED
t "2. 확정자 M 나가기 → LEAVE_DEADLINE_PASSED" M DELETE /api/parties/$R13/members/me "" 409 LEAVE_DEADLINE_PASSED
t "3. 미확정 M2 나가기 → 200" M2 DELETE /api/parties/$R13/members/me "" 200 -

# ===============================================================
section "TS-A-CHAT-03 FINISHED 읽기 전용·DELETED 중 폴링"
party 4; CP=$PID; call M POST /api/parties/$CP/members
sqldo "UPDATE MEAL_PARTY SET STATUS='FINISHED' WHERE PARTY_ID=$CP; COMMIT;"
t "1. FINISHED 팟 대화 조회 → readOnly=true" M GET /api/parties/$CP/chats "" 200 - '"readOnly":true'
t "2. FINISHED 팟 전송 → CHAT_READ_ONLY" M POST /api/parties/$CP/chats '{"content":"hi"}' 409 CHAT_READ_ONLY
party 4; CQ=$PID; call M POST /api/parties/$CQ/members
t "3. 폴링 중 (삭제 전)" M GET /api/parties/$CQ/chats "" 200 - '"readOnly":false'
LASTCID=$(printf '%s' "$LAST" | grep -o '"chatId":[0-9]*' | tail -1 | grep -o '[0-9]*')
call H DELETE /api/parties/$CQ
t "3. 방장 삭제 후 다음 폴링 → PARTY_DELETED" M GET "/api/parties/$CQ/chats?afterId=$LASTCID" "" 404 PARTY_DELETED

# ===============================================================
section "TS-A-THUMBS-01 따봉 요청 대상 조회"
# X2 전용 팟 5개: 어제·오늘 확정 FINISHED(대상), 8일 전, 미확정, 따봉 완료(제외)
party 4; TA=$PID; party 4; TB=$PID; party 4; TOLD=$PID; party 4; TUN=$PID; party 4; TDONE=$PID
for p in $TA $TB $TOLD $TUN $TDONE; do call X2 POST /api/parties/$p/members; done
sqldo "UPDATE MEAL_PARTY SET STATUS='FINISHED' WHERE PARTY_ID IN ($TA,$TB,$TOLD,$TUN,$TDONE);
UPDATE MEAL_PARTY SET PARTY_DATE=TRUNC(SYSDATE)-1 WHERE PARTY_ID=$TA;
UPDATE MEAL_PARTY SET PARTY_DATE=TRUNC(SYSDATE)-8 WHERE PARTY_ID=$TOLD;
UPDATE PARTY_MEMBER SET CONFIRMED_YN='Y' WHERE MEMBER_ID=$(MID X2) AND PARTY_ID IN ($TA,$TB,$TOLD,$TDONE);
UPDATE PARTY_MEMBER SET THUMBS_UP_YN='Y' WHERE MEMBER_ID=$(MID X2) AND PARTY_ID=$TDONE;
COMMIT;"
info "어제=$TA 오늘=$TB (대상) / 8일 전=$TOLD 미확정=$TUN 따봉 완료=$TDONE (제외)"
t "2. 가장 최근 1건 = 오늘 팟 $TB" X2 GET /api/parties/my/thumbs-pending "" 200 - "\"partyId\":$TB"
t "대상 없는 회원(N) → data=null" N GET /api/parties/my/thumbs-pending "" 200 - '"data":null'

section "TS-A-THUMBS-02 따봉 등록 성공"
call X2 GET /api/restaurants/$RID; BEFORE=$(num thumbsCount30d)
info "식당 $RID 최근 30일 따봉 수 (전): $BEFORE"
t "1. 오늘 팟 $TB 따봉 → 200, 30일 따봉 +1" X2 POST /api/parties/$TB/thumbs "" 200 - "\"thumbsCount30d\":$((BEFORE+1))"
check "DB thumbs_up_yn=Y" '[ "$(sql "SELECT THUMBS_UP_YN FROM PARTY_MEMBER WHERE PARTY_ID=$TB AND MEMBER_ID=$(MID X2);")" = Y ]'
t "배너 다음 대상(어제 팟 $TA)으로 바뀜" X2 GET /api/parties/my/thumbs-pending "" 200 - "\"partyId\":$TA"
t "2. 식당 상세 반영" X2 GET /api/restaurants/$RID "" 200 - "\"thumbsCount30d\":$((BEFORE+1))"
t "2. 식당 목록 반영" X2 GET /api/restaurants/list "" 200 - "\"restaurantId\":$RID"
check "2. 목록의 식당 $RID 따봉 = $((BEFORE+1))" 'printf "%s" "$LAST" | grep -q "\"restaurantId\":$RID,[^}]*\"thumbsCount30d\":$((BEFORE+1))"'

section "TS-A-THUMBS-03 중복 따봉 (동시 요청)"
for k in 1 2; do
  curl -s -X POST -c cookies/X2.txt -b cookies/X2.txt -o "tmp/th_$k.json" -w '%{http_code}' "$BASE/api/parties/$TA/thumbs" > "tmp/ths_$k.txt" &
done
wait
S1=$(cat tmp/ths_1.txt); S2=$(cat tmp/ths_2.txt)
E1=$(sed -n 's/.*"error":"\([A-Z_]*\)".*/\1/p' tmp/th_1.json); E2=$(sed -n 's/.*"error":"\([A-Z_]*\)".*/\1/p' tmp/th_2.json)
info "동시 요청 결과: $S1 ${E1:-OK} / $S2 ${E2:-OK}"
check "1. 하나만 200, 나머지 409 ALREADY_THUMBED" '{ [ "$S1" = 200 ] && [ "$E2" = ALREADY_THUMBED ]; } || { [ "$S2" = 200 ] && [ "$E1" = ALREADY_THUMBED ]; }'
t "2. 다시 요청 → ALREADY_THUMBED" X2 POST /api/parties/$TA/thumbs "" 409 ALREADY_THUMBED
t "따봉 수는 1만 증가 (총 +2: THUMBS-02 + 03)" X2 GET /api/restaurants/$RID "" 200 - "\"thumbsCount30d\":$((BEFORE+2))"

section "TS-A-THUMBS-04 따봉 자격 없음"
t "1. SCHEDULED 팟(R13) 확정자 M → PARTY_NOT_FINISHED" M POST /api/parties/$R13/thumbs "" 409 PARTY_NOT_FINISHED
t "2. FINISHED 팟(CP) 미확정 M → NOT_CONFIRMED_MEMBER" M POST /api/parties/$CP/thumbs "" 403 NOT_CONFIRMED_MEMBER
t "3. FINISHED 팟(CP) 비참여 N → NOT_CONFIRMED_MEMBER" N POST /api/parties/$CP/thumbs "" 403 NOT_CONFIRMED_MEMBER

# ===============================================================
section "TS-A-SCHED-01·02 스케줄러 (5분마다) — 실제로 기다림"
PASTT=$(date -d '-10 min' +%H:%M); FUT=$(date -d '+40 min' +%H:%M)
party 4; S_SCH=$PID; call H PATCH /api/parties/$S_SCH/schedule "{\"confirmedTime\":\"$T1\",\"meetPlace\":\"lobby\"}"
party 4; S_REC=$PID
party 4; S_NOT=$PID
party 4; S_DEL=$PID; call H DELETE /api/parties/$S_DEL
party 4; S_PRI=$PID; call H PATCH /api/parties/$S_PRI/schedule "{\"confirmedTime\":\"$T1\",\"meetPlace\":\"lobby\"}"
call X3 POST /api/parties/$S_NOT/members; call X3 POST /api/parties/$S_SCH/members   # 메인 '참여 중' 순서 확인용 (X3 전용)
sqldo "UPDATE MEAL_PARTY SET CONFIRMED_TIME='$PASTT' WHERE PARTY_ID=$S_SCH;
UPDATE MEAL_PARTY SET MEAL_TIME='$PASTT' WHERE PARTY_ID IN ($S_REC,$S_DEL);
UPDATE MEAL_PARTY SET MEAL_TIME='$PASTT', CONFIRMED_TIME='$FUT' WHERE PARTY_ID=$S_PRI;
COMMIT;"
info "확정 시간 지남(SCHEDULED)=$S_SCH, 예정 시간 지남(RECRUITING)=$S_REC, 아직=$S_NOT, DELETED=$S_DEL"
info "SCHED-02: 예정 $PASTT(지남) · 확정 $FUT(안 지남)=$S_PRI"

wait_tick() {  # 다음 5분 정각 + 20초까지 대기
  local now s; now=$(( 10#$(date +%M) * 60 + 10#$(date +%S) )); s=$(( 300 - now % 300 + 20 ))
  info "스케줄러 다음 실행까지 ${s}초 대기…"; sleep "$s"
}
st() { sql "SELECT STATUS FROM MEAL_PARTY WHERE PARTY_ID=$1;"; }
wait_tick
check "SCHED-01 확정 시간 지난 SCHEDULED → FINISHED" '[ "$(st $S_SCH)" = FINISHED ]' "$(st $S_SCH)"
check "SCHED-01 예정 시간 지난 RECRUITING → FINISHED" '[ "$(st $S_REC)" = FINISHED ]' "$(st $S_REC)"
check "SCHED-01 아직 시간 안 된 팟 → 변화 없음" '[ "$(st $S_NOT)" = RECRUITING ]' "$(st $S_NOT)"
check "SCHED-01 DELETED 팟 → 변화 없음" '[ "$(st $S_DEL)" = DELETED ]' "$(st $S_DEL)"
t "SCHED-01 완료 시스템 메시지" H GET /api/parties/$S_SCH/chats "" 200 - '식사가 완료되었습니다'
call H GET /api/parties/list
check "SCHED-01 목록에서 사라짐" '! has_id $S_SCH && ! has_id $S_REC'
call X3 GET /api/parties/my/today
TODAY_ST=$(printf '%s' "$LAST" | grep -o '"status":"[A-Z]*"' | grep -o '[A-Z]*' | tr '\n' ' ')
info "X3의 '참여 중' 상태 순서: $TODAY_ST (기대: RECRUITING FINISHED)"
check "SCHED-01 '참여 중'에 남고 FINISHED가 뒤쪽" '[ "$TODAY_ST" = "RECRUITING FINISHED " ]' "$TODAY_ST"
check "SCHED-02 1. 예정 시간 지났지만 확정 시간 전 → FINISHED 아님" '[ "$(st $S_PRI)" = SCHEDULED ]' "$(st $S_PRI)"

sqldo "UPDATE MEAL_PARTY SET CONFIRMED_TIME='$(date -d '-5 min' +%H:%M)' WHERE PARTY_ID=$S_PRI; COMMIT;"
info "SCHED-02: 확정 시간을 5분 전으로 바꾸고 다음 실행 대기"
wait_tick
check "SCHED-02 2. 확정 시간 지난 뒤 → FINISHED" '[ "$(st $S_PRI)" = FINISHED ]' "$(st $S_PRI)"

summary
echo
echo "팟 ID: PARTY-03 [$Q_REC $Q_SCH $Q_FULL $Q_CLO $Q_FIN $Q_DEL $Q_YES] PARTY-13 [$R13] CHAT-03 [$CP $CQ]"
echo "       THUMBS [$TA $TB $TOLD $TUN $TDONE] SCHED [$S_SCH $S_REC $S_NOT $S_DEL $S_PRI]"
echo "이 출력 전체를 Claude에게 붙여 주세요. (round23-result.txt에도 저장됨)"
