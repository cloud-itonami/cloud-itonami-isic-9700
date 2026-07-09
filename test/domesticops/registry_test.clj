(ns domesticops.registry-test
  (:require [clojure.test :refer [deftest is]]
            [domesticops.registry :as r]))

;; ----------------------------- payroll-matches-contract? -----------------------------

(deftest matches-when-claim-equals-kotoba-labor-recompute
  (is (r/payroll-matches-contract?
       {:assignment-id "a1" :worker "w" :household "h" :role "housekeeper"
        :wage-type :hourly :rate 1500 :hours-worked 8 :claimed-gross 12000})))

(deftest mismatches-when-claim-differs-from-kotoba-labor-recompute
  (is (not (r/payroll-matches-contract?
            {:assignment-id "a3" :worker "w" :household "h" :role "gardener"
             :wage-type :hourly :rate 1600 :hours-worked 5 :claimed-gross 10000}))))

(deftest compute-gross-wages-delegates-to-kotoba-labor
  (is (= 12000 (r/compute-gross-wages
                {:assignment-id "a1" :worker "w" :household "h" :role "housekeeper"
                 :wage-type :hourly :rate 1500 :hours-worked 8}))))

(deftest compute-gross-wages-returns-nil-for-unknown-wage-type
  (is (nil? (r/compute-gross-wages
             {:assignment-id "a1" :worker "w" :household "h" :role "housekeeper"
              :wage-type :weekly :rate 1500 :hours-worked 8}))))

;; ----------------------------- register-mission-dispatch -----------------------------

(deftest dispatch-is-a-draft-not-a-real-dispatch
  (let [result (r/register-mission-dispatch "assignment-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest dispatch-assigns-dispatch-number
  (let [result (r/register-mission-dispatch "assignment-1" "JPN" 7)]
    (is (= (get result "dispatch_number") "JPN-DSP-000007"))
    (is (= (get-in result ["record" "assignment_id"]) "assignment-1"))
    (is (= (get-in result ["record" "kind"]) "mission-dispatch-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest dispatch-validation-rules
  (is (thrown? Exception (r/register-mission-dispatch "" "JPN" 0)))
  (is (thrown? Exception (r/register-mission-dispatch "assignment-1" "" 0)))
  (is (thrown? Exception (r/register-mission-dispatch "assignment-1" "JPN" -1))))

;; ----------------------------- register-payroll-posting -----------------------------

(deftest payroll-is-a-draft-not-a-real-payroll
  (let [result (r/register-payroll-posting "assignment-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest payroll-assigns-payroll-number
  (let [result (r/register-payroll-posting "assignment-1" "JPN" 7)]
    (is (= (get result "payroll_number") "JPN-PAY-000007"))
    (is (= (get-in result ["record" "assignment_id"]) "assignment-1"))
    (is (= (get-in result ["record" "kind"]) "payroll-posting-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest payroll-validation-rules
  (is (thrown? Exception (r/register-payroll-posting "" "JPN" 0)))
  (is (thrown? Exception (r/register-payroll-posting "assignment-1" "" 0)))
  (is (thrown? Exception (r/register-payroll-posting "assignment-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-mission-dispatch "assignment-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-mission-dispatch "assignment-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DSP-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DSP-000001" (get-in hist2 [1 "record_id"])))))
