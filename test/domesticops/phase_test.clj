(ns domesticops.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:assignment/dispatch`/`:assignment/pay` must NEVER be
  a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [domesticops.phase :as phase]))

(deftest assignment-dispatch-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real mission dispatch"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :assignment/dispatch))
          (str "phase " n " must not auto-commit :assignment/dispatch")))))

(deftest assignment-pay-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real payroll posting"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :assignment/pay))
          (str "phase " n " must not auto-commit :assignment/pay")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-household-facing-risk-ops
  (testing ":assignment/intake carries no direct household-facing risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:assignment/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :assignment/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :assignment/dispatch} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :assignment/pay} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :assignment/intake} :commit)))))
