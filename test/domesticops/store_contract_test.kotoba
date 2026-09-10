(ns domesticops.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [domesticops.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "JPN" (:jurisdiction (store/assignment s "assignment-1"))))
      (is (= 12000 (:claimed-gross (store/assignment s "assignment-1"))))
      (is (true? (:household-employment-registered? (store/assignment s "assignment-1"))))
      (is (false? (:involves-vulnerable-person? (store/assignment s "assignment-1"))))
      (is (= 10000 (:claimed-gross (store/assignment s "assignment-3"))))
      (is (false? (:household-employment-registered? (store/assignment s "assignment-4"))))
      (is (true? (:involves-vulnerable-person? (store/assignment s "assignment-5"))))
      (is (false? (:safeguarding-check-verified? (store/assignment s "assignment-5"))))
      (is (true? (:safeguarding-check-verified? (store/assignment s "assignment-6"))))
      (is (false? (:dispatched? (store/assignment s "assignment-1"))))
      (is (false? (:paid? (store/assignment s "assignment-1"))))
      (is (= ["assignment-1" "assignment-2" "assignment-3" "assignment-4" "assignment-5" "assignment-6"]
             (mapv :id (store/all-assignments s))))
      (is (nil? (store/assessment-of s "assignment-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/dispatch-history s)))
      (is (= [] (store/payroll-history s)))
      (is (zero? (store/next-dispatch-sequence s "JPN")))
      (is (zero? (store/next-payroll-sequence s "JPN")))
      (is (false? (store/assignment-already-dispatched? s "assignment-1")))
      (is (false? (store/assignment-already-paid? s "assignment-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :assignment/upsert
                                 :value {:id "assignment-1" :worker "Kita Sato"}})
        (is (= "Kita Sato" (:worker (store/assignment s "assignment-1"))))
        (is (= 12000 (:claimed-gross (store/assignment s "assignment-1"))) "unrelated field preserved"))
      (testing "assessment payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["assignment-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "assignment-1"))))
      (testing "dispatch drafts a record and advances the dispatch sequence"
        (store/commit-record! s {:effect :assignment/mark-dispatched :path ["assignment-1"]})
        (is (= "JPN-DSP-000000" (get (first (store/dispatch-history s)) "record_id")))
        (is (= "mission-dispatch-draft" (get (first (store/dispatch-history s)) "kind")))
        (is (true? (:dispatched? (store/assignment s "assignment-1"))))
        (is (= 1 (count (store/dispatch-history s))))
        (is (= 1 (store/next-dispatch-sequence s "JPN")))
        (is (true? (store/assignment-already-dispatched? s "assignment-1"))))
      (testing "payroll posting drafts a record and advances the payroll sequence"
        (store/commit-record! s {:effect :assignment/mark-paid :path ["assignment-1"]})
        (is (= "JPN-PAY-000000" (get (first (store/payroll-history s)) "record_id")))
        (is (= "payroll-posting-draft" (get (first (store/payroll-history s)) "kind")))
        (is (true? (:paid? (store/assignment s "assignment-1"))))
        (is (= 1 (count (store/payroll-history s))))
        (is (= 1 (store/next-payroll-sequence s "JPN")))
        (is (true? (store/assignment-already-paid? s "assignment-1"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/assignment s "nope")))
    (is (= [] (store/all-assignments s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/dispatch-history s)))
    (is (= [] (store/payroll-history s)))
    (is (zero? (store/next-dispatch-sequence s "JPN")))
    (is (zero? (store/next-payroll-sequence s "JPN")))
    (store/with-assignments s {"x" {:id "x" :worker "w" :household "h" :role "r"
                                    :wage-type :hourly :rate 1 :hours-worked 1 :claimed-gross 1
                                    :household-employment-registered? true
                                    :involves-vulnerable-person? false :safeguarding-check-verified? false
                                    :dispatched? false :paid? false
                                    :jurisdiction "JPN" :status :intake}})
    (is (= "w" (:worker (store/assignment s "x"))))))
