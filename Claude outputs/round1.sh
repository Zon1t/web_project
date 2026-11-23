#!/usr/bin/env bash
# round1.sh — A파트 라운드 1: API만으로 확인하는 시나리오 (C 작성)
#   PARTY-01·02·04·05·06·07·08(1·2·4)·09·10·11·12·14, CHAT-01·02
# 실행: Git Bash에서  cd 이 폴더 → bash round1.sh 2>&1 | tee round1-result.txt
# 데이터: TEST_ 계정 8개와 그 계정들이 만든 식사팟만 생긴다. 정리는 cleanup.sql

cd "$(dirname "$0")" || exit 1
source ./lib.sh

section "준비: 서버·계정·식당·시간"
call H GET /api/auth/me
if [ "$LAST_STATUS" != "401" ] && [ "$LAST_STATUS" != "200" ]; then
  echo "서버 응답 없음 ($BASE, 상태 '$LAST_STATUS'). 서버를 8081로 켜고 다시 실행해 주세요."; exit 1
fi
for u in H M M2 N L X1 X2 X3; do
  call "$u" POST /api/auth/signup "{\"loginId\":\"${LOGIN[$u]}\",\"password\":\"$PW\",\"passwordConfirm\":\"$PW\",\"nickname\":\"${NICK[$u]}\"}"
  call "$u" POST /api/auth/login "{\"loginId\":\"${LOGIN[$u]}\",\"password\":\"$PW\"}"
  [ "$LAST_STATUS" = "200" ] || { echo "$u 로그인 실패: $LAST"; exit 1; }
done
info "테스트 계정 8개 로그인 완료"

call H GET /api/restaurants/roulette/candidates
RID=$(num restaurantId)
[ -n "$RID" ] || { echo "식당을 찾지 못했습니다 (구내식당 말고 식당이 1곳 이상 있어야 함): $LAST"; exit 1; }
pick_times
info "식당 ID=$RID, 끼니=$MEAL, T1=$T1, T2=$T2, 다른 끼니 시간=$WRONG, 지난 시간=${PAST:-없음}"

party() {  # party 정원 → 새 팟 ID를 PID에 (H가 방장)
  call H POST /api/parties/create "{\"restaurantId\":$RID,\"mealType\":\"$MEAL\",\"mealTime\":\"$T1\",\"capacity\":$1,\"memo\":\"TEST_테스트 팟\"}"
  PID=$(num partyId)
  [ -n "$PID" ] || { echo "팟 생성 실패: $LAST"; exit 1; }
}

# ---------------------------------------------------------------
section "TS-A-PARTY-01 식사팟 생성 성공"
t "1. 생성 200" H POST /api/parties/create "{\"restaurantId\":$RID,\"mealType\":\"$MEAL\",\"mealTime\":\"$T1\",\"capacity\":4,\"memo\":\"TEST_같이 드실 분\"}" 200 - '"partyId"'
P1=$(num partyId); info "P1=$P1 (DB 확인은 check.sql 1번)"
t "상세: 방장 HOST·RECRUITING·일정 null" H GET /api/parties/$P1 "" 200 - '"myRole":"HOST"' '"status":"RECRUITING"' '"schedule":null' '"memberCount":1'
t "대화: 생성 시스템 메시지" H GET /api/parties/$P1/chats "" 200 - 'TEST_방장님이 식사팟을 만들었습니다'

section "TS-A-PARTY-02 식사팟 생성 검증 실패"
body() { printf '{"restaurantId":%s,"mealType":"%s","mealTime":"%s","capacity":%s,"memo":"%s"}' "$1" "$MEAL" "$2" "$3" "$4"; }
t "1. 다른 끼니 시간($WRONG)" H POST /api/parties/create "$(body $RID $WRONG 4 x)" 400 INVALID_MEAL_TIME
if [ "$MEAL" = LUNCH ]; then ODD="12:15"; else ODD="18:15"; fi
t "2. 30분 단위 아님($ODD)" H POST /api/parties/create "$(body $RID $ODD 4 x)" 400 INVALID_MEAL_TIME
if [ -n "$PAST" ]; then
  t "3. 지난 시간($PAST)" H POST /api/parties/create "$(body $RID $PAST 4 x)" 400 INVALID_MEAL_TIME
else
  info "보류  3. 지난 시간: 지금은 $MEAL 시간대에 지난 슬롯이 없음 (점심이면 11:30 이후에 다시 확인)"
fi
t "4a. 정원 1" H POST /api/parties/create "$(body $RID $T1 1 x)" 400 INVALID_REQUEST
t "4b. 정원 13" H POST /api/parties/create "$(body $RID $T1 13 x)" 400 INVALID_REQUEST
MEMO51=$(printf '가%.0s' $(seq 1 51))
t "5. 한마디 51자" H POST /api/parties/create "$(body $RID $T1 4 $MEMO51)" 400 INVALID_REQUEST
t "6. 없는 식당" H POST /api/parties/create "$(body 999999 $T1 4 x)" 404 RESTAURANT_NOT_FOUND
info "저장 안 됐는지는 check.sql 2번 (H의 팟이 스크립트가 만든 것만 있어야 함)"

# ---------------------------------------------------------------
section "TS-A-PARTY-05 식사팟 참여 (1차) 성공"
t "M 참여 200" M POST /api/parties/$P1/members "" 200 - '"memberCount":2' '"status":"RECRUITING"'
t "상세: M은 MEMBER, 미확정" M GET /api/parties/$P1 "" 200 - '"myRole":"MEMBER"' '"myConfirmed":false'
t "대화: 참여 시스템 메시지 (대화창 열림)" M GET /api/parties/$P1/chats "" 200 - 'TEST_참여님이 참여했습니다'

section "TS-A-PARTY-04 식사팟 상세 조회 (역할)"
t "1. H → HOST" H GET /api/parties/$P1 "" 200 - '"myRole":"HOST"'
t "1. M → MEMBER" M GET /api/parties/$P1 "" 200 - '"myRole":"MEMBER"'
t "1. N → NONE" N GET /api/parties/$P1 "" 200 - '"myRole":"NONE"'
t "2. 일정 확정 전 schedule=null" M GET /api/parties/$P1 "" 200 - '"schedule":null'
t "3. 없는 partyId" H GET /api/parties/999999 "" 404 PARTY_NOT_FOUND

section "TS-A-PARTY-12 참여 확정·확정 취소 (2차)"
t "1. 일정 전 확정 → SCHEDULE_NOT_SET" M POST /api/parties/$P1/members/me/confirm "" 409 SCHEDULE_NOT_SET

section "TS-A-PARTY-11 일정 확정·수정 (방장)"
t "1. H 일정 확정 $T1 1층 로비" H PATCH /api/parties/$P1/schedule "{\"confirmedTime\":\"$T1\",\"meetPlace\":\"1층 로비\"}" 200 - '"status":"SCHEDULED"' '"confirmationsReset":false'
t "1. 시스템 메시지" H GET /api/parties/$P1/chats "" 200 - "방장이 일정을 확정했습니다 · $T1 · 1층 로비"
t "PARTY-04 2. 확정 후 schedule 있음" M GET /api/parties/$P1 "" 200 - "\"confirmedTime\":\"$T1\"" '"cancelDeadline"'
info "PARTY-04 cancelDeadline = 오늘 $T1 - 30분 이어야 함 → 응답: $(printf '%s' "$LAST" | sed -n 's/.*"cancelDeadline":"\([^"]*\)".*/\1/p')"
t "2. M 참여 확정 (PARTY-12 2)" M POST /api/parties/$P1/members/me/confirm "" 200 - '"confirmed":true' '"confirmedCount":1'
t "PARTY-12 3. 다시 확정 → ALREADY_CONFIRMED" M POST /api/parties/$P1/members/me/confirm "" 409 ALREADY_CONFIRMED
t "3. H 일정 수정 $T2 정문 → 확정 초기화" H PATCH /api/parties/$P1/schedule "{\"confirmedTime\":\"$T2\",\"meetPlace\":\"정문\"}" 200 - '"confirmationsReset":true'
t "3. M 확정 풀림" M GET /api/parties/$P1 "" 200 - '"myConfirmed":false' '"confirmedCount":0'
t "3. 변경 시스템 메시지" H GET /api/parties/$P1/chats "" 200 - "방장이 일정을 변경했습니다 · $T2 · 정문"
t "5. M이 일정 확정 → NOT_HOST" M PATCH /api/parties/$P1/schedule "{\"confirmedTime\":\"$T1\",\"meetPlace\":\"x\"}" 403 NOT_HOST
t "6. 다른 끼니 시간($WRONG)" H PATCH /api/parties/$P1/schedule "{\"confirmedTime\":\"$WRONG\",\"meetPlace\":\"x\"}" 400 INVALID_MEAL_TIME

section "TS-A-PARTY-12 (이어서)"
t "M 다시 확정" M POST /api/parties/$P1/members/me/confirm "" 200 - '"confirmed":true'
t "4. 확정 취소 (마감 전)" M DELETE /api/parties/$P1/members/me/confirm "" 200 - '"confirmed":false'
t "4. 취소 후에도 MEMBER·대화 가능" M GET /api/parties/$P1/chats "" 200 -
t "5. 다시 취소 → NOT_CONFIRMED" M DELETE /api/parties/$P1/members/me/confirm "" 409 NOT_CONFIRMED

section "TS-A-PARTY-08 중복 참여 (1)"
t "1. M이 P1 다시 참여 → ALREADY_JOINED" M POST /api/parties/$P1/members "" 409 ALREADY_JOINED

# ---------------------------------------------------------------
section "TS-A-CHAT-01 대화 전송·폴링"
call H GET /api/parties/$P1/chats; BEFORE=$(printf '%s' "$LAST" | grep -o '"chatId":[0-9]*' | tail -1 | grep -o '[0-9]*')
t "1. H가 '12시에 봬요' 전송" H POST /api/parties/$P1/chats '{"content":"12시에 봬요"}' 200 - '"chatId"'
CID=$(num chatId)
t "2. M 폴링(afterId=$BEFORE) → 새 메시지만, mine=false" M GET "/api/parties/$P1/chats?afterId=$BEFORE" "" 200 - '12시에 봬요' '"mine":false' '"type":"TEXT"'
info "M 폴링 결과 메시지 수: $(printf '%s' "$LAST" | grep -o '"chatId"' | wc -l) (1이어야 함)"
t "2. H 화면은 mine=true" H GET "/api/parties/$P1/chats?afterId=$BEFORE" "" 200 - '"mine":true'
t "2. afterId=$CID → 빈 배열" M GET "/api/parties/$P1/chats?afterId=$CID" "" 200 - '"chats":[]'
t "3a. 공백만" H POST /api/parties/$P1/chats '{"content":"   "}' 400 INVALID_REQUEST '메시지를 입력해 주세요.'
MSG201=$(printf 'a%.0s' $(seq 1 201))
t "3b. 201자" H POST /api/parties/$P1/chats "{\"content\":\"$MSG201\"}" 400 INVALID_REQUEST

section "TS-A-CHAT-02 비참여자·나간 사람 대화 차단"
t "1. N 조회 → NOT_JOINED" N GET /api/parties/$P1/chats "" 403 NOT_JOINED '참여자만 대화를 볼 수 있습니다.'
t "2. N 전송 → NOT_JOINED" N POST /api/parties/$P1/chats '{"content":"hi"}' 403 NOT_JOINED
call L POST /api/parties/$P1/members; call L DELETE /api/parties/$P1/members/me
info "L 참여 후 나감 (나가기 응답 $LAST_STATUS)"
t "3. 나간 L 조회 → NOT_JOINED" L GET /api/parties/$P1/chats "" 403 NOT_JOINED
t "PARTY-09 3. 미참여자 N 나가기 → NOT_JOINED" N DELETE /api/parties/$P1/members/me "" 409 NOT_JOINED

# ---------------------------------------------------------------
section "TS-A-PARTY-06 정원 도달 시 자동 FULL"
party 4; P2=$PID; info "P2=$P2 (정원 4)"
call H PATCH /api/parties/$P2/schedule "{\"confirmedTime\":\"$T1\",\"meetPlace\":\"정문\"}"
call M POST /api/parties/$P2/members; call M2 POST /api/parties/$P2/members
t "준비: SCHEDULED 3/4" H GET /api/parties/$P2 "" 200 - '"status":"SCHEDULED"' '"memberCount":3'
t "1. X1 참여 → 4/4 FULL" X1 POST /api/parties/$P2/members "" 200 - '"memberCount":4' '"status":"FULL"'
t "2. N 참여 → PARTY_FULL" N POST /api/parties/$P2/members "" 409 PARTY_FULL '이미 마감된 식사팟입니다.'

section "TS-A-PARTY-09 참여자 나가기 및 FULL 해제"
t "1. 미확정 M 나가기" M DELETE /api/parties/$P2/members/me "" 200 - '"partyDeleted":false'
t "1. FULL → SCHEDULED, 3/4" H GET /api/parties/$P2 "" 200 - '"status":"SCHEDULED"' '"memberCount":3'
t "1. 나감 메시지, 기존 대화 유지" H GET /api/parties/$P2/chats "" 200 - 'TEST_참여님이 나갔습니다' 'TEST_참여님이 참여했습니다'

section "TS-A-PARTY-14 모집 마감·재개"
party 4; P3=$PID; info "P3=$P3"
call M2 POST /api/parties/$P3/members
t "1. H 모집 마감" H PATCH /api/parties/$P3/status '{"action":"CLOSE"}' 200 - '"status":"CLOSED"'
t "1. 마감 시스템 메시지" H GET /api/parties/$P3/chats "" 200 - '방장이 모집을 마감했습니다'
t "1. 마감 후에도 대화 가능" M2 POST /api/parties/$P3/chats '{"content":"마감 후 대화"}' 200 -
t "2. N 참여 → PARTY_FULL (PARTY-08 2)" N POST /api/parties/$P3/members "" 409 PARTY_FULL
t "3. 다시 CLOSE → INVALID_STATUS" H PATCH /api/parties/$P3/status '{"action":"CLOSE"}' 409 INVALID_STATUS
t "6. M2가 마감 → NOT_HOST" M2 PATCH /api/parties/$P3/status '{"action":"CLOSE"}' 403 NOT_HOST
t "4. 재개 → RECRUITING (일정 없음, 2/4)" H PATCH /api/parties/$P3/status '{"action":"REOPEN"}' 200 - '"status":"RECRUITING"'
t "5. RECRUITING에서 REOPEN → INVALID_STATUS" H PATCH /api/parties/$P3/status '{"action":"REOPEN"}' 409 INVALID_STATUS

section "TS-A-PARTY-09 (이어서) CLOSED 팟 나가기"
call H PATCH /api/parties/$P3/status '{"action":"CLOSE"}'
t "2. M2가 CLOSED 팟 나가기" M2 DELETE /api/parties/$P3/members/me "" 200 -
t "2. CLOSED 유지" H GET /api/parties/$P3 "" 200 - '"status":"CLOSED"'

# ---------------------------------------------------------------
section "TS-A-PARTY-10 방장 나가기·삭제"
party 4; P4=$PID; call M POST /api/parties/$P4/members
party 4; P5=$PID; call M POST /api/parties/$P5/members
info "P4=$P4, P5=$P5 (둘 다 M 참여)"
t "3. M이 P5 삭제 → NOT_HOST (삭제 전)" M DELETE /api/parties/$P5 "" 403 NOT_HOST
t "2. H가 P5 삭제" H DELETE /api/parties/$P5 "" 200 -
t "4. P5 상세 → PARTY_DELETED" H GET /api/parties/$P5 "" 404 PARTY_DELETED
t "4. P5 초대 링크 → PARTY_DELETED" H GET /api/parties/$P5/invite-link "" 404 PARTY_DELETED
t "PARTY-08 4. N이 P5 참여 → PARTY_DELETED" N POST /api/parties/$P5/members "" 404 PARTY_DELETED
t "1. H가 P4 나가기 → partyDeleted=true" H DELETE /api/parties/$P4/members/me "" 200 - '"partyDeleted":true'
t "1. P4 상세 → PARTY_DELETED" M GET /api/parties/$P4 "" 404 PARTY_DELETED
call H GET /api/parties/list
if printf '%s' "$LAST" | grep -qE "\"partyId\":($P4|$P5)[,}]"; then FAIL=$((FAIL+1)); echo "  FAIL  4. 목록에 삭제된 팟이 보임"; else PASS=$((PASS+1)); echo "  PASS  4. 목록에서 P4·P5 제외"; fi
call M GET /api/users/me/parties
if printf '%s' "$LAST" | grep -qE "\"partyId\":($P4|$P5)[,}]"; then FAIL=$((FAIL+1)); echo "  FAIL  4. 마이페이지에 삭제된 팟이 보임"; else PASS=$((PASS+1)); echo "  PASS  4. 마이페이지 참여 팟에서 P4·P5 제외"; fi
info "DB에 행이 남아 있는지는 check.sql 3번"

# ---------------------------------------------------------------
section "TS-A-PARTY-07 마지막 1자리 동시 참여"
party 4; P6=$PID; call M POST /api/parties/$P6/members; call M2 POST /api/parties/$P6/members
info "P6=$P6 (3/4). X1·X2·X3 동시 참여"
for u in X1 X2 X3; do
  curl -s -X POST -c "cookies/$u.txt" -b "cookies/$u.txt" -o "tmp/c_$u.json" -w "$u %{http_code}\n" "$BASE/api/parties/$P6/members" > "tmp/s_$u.txt" &
done
wait
OK200=0; FULL409=0
for u in X1 X2 X3; do
  s=$(cut -d' ' -f2 "tmp/s_$u.txt"); e=$(sed -n 's/.*"error":"\([A-Z_]*\)".*/\1/p' "tmp/c_$u.json")
  info "$u → $s ${e:-OK}"
  [ "$s" = 200 ] && OK200=$((OK200+1)); [ "$s" = 409 ] && [ "$e" = PARTY_FULL ] && FULL409=$((FULL409+1))
done
if [ $OK200 = 1 ] && [ $FULL409 = 2 ]; then PASS=$((PASS+1)); echo "  PASS  1. 200 한 명, PARTY_FULL 두 명"; else FAIL=$((FAIL+1)); echo "  FAIL  1. 200 $OK200명, PARTY_FULL $FULL409명 (기대 1·2)"; fi
t "2. 4/4 FULL (정원 초과 없음)" H GET /api/parties/$P6 "" 200 - '"memberCount":4' '"status":"FULL"'
info "참여 시스템 메시지 수는 check.sql 4번"

summary
echo
echo "팟 ID: P1=$P1 P2=$P2 P3=$P3 P4=$P4 P5=$P5 P6=$P6"
echo "이 출력 전체를 Claude에게 붙여 주세요. (round1-result.txt에도 저장됨)"
