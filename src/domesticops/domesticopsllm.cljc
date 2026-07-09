(ns domesticops.domesticopsllm
  "DomesticOps-LLM client -- the *contained intelligence node* for the
  community-domestic-employment actor.

  It normalizes assignment intake, drafts a per-jurisdiction
  household-employer-registration/safeguarding evidence checklist,
  drafts the mission-dispatch action, and drafts the payroll-posting
  action. CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real dispatch/payroll. Every output is
  censored downstream by `domesticops.governor` before anything
  touches the SSoT, and `:assignment/dispatch`/`:assignment/pay`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/dispatch-mission | :actuation/post-payroll | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [domesticops.facts :as facts]
            [domesticops.registry :as registry]
            [domesticops.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the worker, rate/hours or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "派遣記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :assignment/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction household-employer-registration/safeguarding
  evidence checklist draft. `:no-spec?` injects the failure mode we
  must defend against: proposing a checklist for a jurisdiction with
  NO official spec-basis in `domesticops.facts` -- the Domestic
  Employment Governor must reject this (never invent a jurisdiction's
  requirements)."
  [db {:keys [subject no-spec?]}]
  (let [a (store/assignment db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction a))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "domesticops.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- propose-dispatch
  "Draft the actual MISSION-DISPATCH action -- dispatching a real
  service robot / domestic worker into a real household. ALWAYS
  `:stake :actuation/dispatch-mission` -- this is a REAL-WORLD act (a
  worker/robot physically enters a private household), never a draft
  the actor may auto-run. See README `Actuation`: no phase ever adds
  this op to a phase's `:auto` set (`domesticops.phase`); the governor
  also always escalates on `:actuation/dispatch-mission`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [a (store/assignment db subject)]
    {:summary    (str subject " 向け派遣提案"
                      (when a (str " (worker=" (:worker a) ")")))
     :rationale  (if a
                   (str "household-employment-registered?=" (:household-employment-registered? a)
                        " involves-vulnerable-person?=" (:involves-vulnerable-person? a)
                        " jurisdiction=" (:jurisdiction a))
                   "assignmentが見つかりません")
     :cites      (if a [subject] [])
     :effect     :assignment/mark-dispatched
     :value      {:assignment-id subject}
     :stake      :actuation/dispatch-mission
     :confidence (if (and a (:household-employment-registered? a)
                       (or (not (:involves-vulnerable-person? a)) (:safeguarding-check-verified? a)))
                   0.9 0.3)}))

(defn- propose-payroll
  "Draft the actual PAYROLL-POSTING action -- posting a real payroll
  to a real worker (triggering wage payment). ALWAYS `:stake
  :actuation/post-payroll` -- this is a REAL-WORLD act (real money
  moves to a real worker), never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`domesticops.phase`); the governor also always escalates on
  `:actuation/post-payroll`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [a (store/assignment db subject)
        payroll-ok? (and a (registry/payroll-matches-contract? a))]
    {:summary    (str subject " 向け給与支払提案"
                      (when a (str " (worker=" (:worker a) ")")))
     :rationale  (if a
                   (str "claimed-gross=" (:claimed-gross a)
                        " kotoba.labor/wages-for-recompute=" (registry/compute-gross-wages a))
                   "assignmentが見つかりません")
     :cites      (if a [subject] [])
     :effect     :assignment/mark-paid
     :value      {:assignment-id subject}
     :stake      :actuation/post-payroll
     :confidence (if payroll-ok? 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :assignment/intake            (normalize-intake db request)
    :jurisdiction/assess               (assess-jurisdiction db request)
    :assignment/dispatch                    (propose-dispatch db request)
    :assignment/pay                              (propose-payroll db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは地域家事使用人雇用事業者の派遣・給与支払エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:assignment/upsert|:assessment/set|:assignment/mark-dispatched|"
       ":assignment/mark-paid) "
       ":stake(:actuation/dispatch-mission か :actuation/post-payroll か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"
       "世帯主登録の状況や身元確認の完了状況を偽って報告してはいけません。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess    {:assignment (store/assignment st subject)}
    :assignment/dispatch    {:assignment (store/assignment st subject)}
    :assignment/pay         {:assignment (store/assignment st subject)}
    {:assignment (store/assignment st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Domestic Employment
  Governor escalates/holds -- an LLM hiccup can never auto-dispatch a
  mission or auto-post a payroll."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :domesticopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
