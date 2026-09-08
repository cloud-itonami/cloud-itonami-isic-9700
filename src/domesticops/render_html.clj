(ns domesticops.render-html
  "Build-time operator-console renderer.

  This namespace RUNS the real actor -- `domesticops.operation/build`
  over a real `domesticops.store/seed-db`, driven through
  `langgraph.graph/run*` -- and renders `docs/samples/operator-console.html`
  from the ACTUAL run output: the governor's own verdicts, the audit
  ledger the `:commit`/`:hold` nodes actually wrote, and the register
  state those commits actually produced.

  Nothing on the page is authored by hand. Every id, worker name,
  amount, rule and verdict is read back out of the store after the run.
  If a value cannot be derived from a run, it is not printed.

  Provenance note -- the file this build overwrites:
  `docs/samples/operator-console.html` was committed in this repo's ROOT
  commit (0f144da, 2026-07-01), which contained no `src/` at all. It was
  produced by `kotoba.labor.ui/dashboard` -- a renderer in the DEPENDENCY
  library -- over that library's own unit-test placeholder data
  (`labor/test/kotoba/labor/ui_test.cljc`: contract \"C1\" worker
  \"worker\" role \"nanny\" hourly 1500; payroll \"P1\" period \"2026-07\"
  gross 12000 deductions 2000). None of those ids, and no `USD` amount
  and no deductions concept, occur anywhere in THIS repo. It showed no
  governor verdict of any kind while badging itself `governor-gated`.
  This build supersedes it.

  Styling is jp-go-dds (デジタル庁デザインシステム), this workspace's
  BASE design system. It is applied through `jp-go-dds.skin/dds+skin`,
  the compat skin written FOR these cloud-itonami operator consoles:
  it restyles semantic markup plus a small class vocabulary
  (`.card` `.badge` `.tag` `.ok` `.warn` `.err` `.critical` `.muted`
  `.amt`) with CSS alone, so adopting DADS reshaped no markup and
  dropped no section. `extra-rules` below adds only what the skin does
  not carry, in DADS custom properties -- no raw hex.

  Determinism: the page contains no wall-clock value and no per-run
  identifier. Building twice yields byte-identical output."
  (:require [clojure.java.io :as io]
            [kotoba.lang.text :as str]
            [css.core :as css]
            [html.core :as html]
            [jp-go-dds.skin :as skin]
            [langgraph.graph :as g]
            [domesticops.operation :as op]
            [domesticops.store :as store]))

;; ---------------------------------------------------------------------------
;; Driving the real actor
;; ---------------------------------------------------------------------------

(def operator
  "The human household-employer operator context injected into every run."
  {:actor-id "op-1" :actor-role :household-employer :phase 3})

(defn- run-step!
  "Execute one operation against `actor`, optionally resuming the
  human-approval interrupt with `approval`. Returns the final state map
  plus the ledger facts THIS step appended to `db`."
  [db actor {:keys [tid request context approval]}]
  (let [before (count (store/ledger db))
        st     (:state (g/run* actor {:request request :context context}
                               {:thread-id tid}))
        st     (if approval
                 (:state (g/run* actor {:approval approval}
                                 {:thread-id tid :resume? true}))
                 st)]
    {:state st
     :new-facts (vec (drop before (store/ledger db)))}))

;; ---------------------------------------------------------------------------
;; Classification -- fact TYPE first, never `:violations` alone
;; ---------------------------------------------------------------------------

(defn classify-fact
  "Classify one ledger fact.

  Dispatch is on `:t` FIRST, then on the presence of `:phase-reason`.
  `:violations` is deliberately NOT the discriminator: an
  `:approval-rejected` fact carries `:violations [{:rule
  :approver-rejected}]` even though a human -- not the governor --
  refused it, and a `:phase-disabled` hold carries an EMPTY
  `:violations` even though it is a genuine refusal to write.

    :governor-refusal -- the Domestic Employment Governor found a HARD
                         violation. No approver can override it.
    :phase-gate       -- the governor was clean; the rollout phase gate
                         declined the write (`domesticops.phase`).
    :approver-rejection -- the governor and phase both cleared it and a
                         human operator declined at the interrupt.
    :commit           -- written to the SSoT."
  [f]
  (cond
    (= :approval-rejected (:t f))                    :approver-rejection
    (and (= :governor-hold (:t f)) (:phase-reason f)) :phase-gate
    (= :governor-hold (:t f))                        :governor-refusal
    (= :committed (:t f))                            :commit
    :else                                            :other))

(def ^:private class-label
  {:governor-refusal   "GOVERNOR HOLD (hard)"
   :phase-gate         "phase gate"
   :approver-rejection "approver rejected"
   :commit             "committed"
   :other              "other"})

(def ^:private class-css
  {:governor-refusal   "critical"
   :phase-gate         "warn"
   :approver-rejection "err"
   :commit             "ok"
   :other              "muted"})

;; ---------------------------------------------------------------------------
;; Scenarios -- the primary lifecycle, mirroring domesticops.sim
;; ---------------------------------------------------------------------------

(def scenarios
  "The primary run. `:intent` records what this step is EXPECTED to
  demonstrate; it is used only to label the scenario table and to drive
  the discriminator test (see `hard-holds` docstring). The classification
  shown on the page is always derived from the ledger fact the actor
  actually wrote, never from `:intent`."
  [{:tid "t1" :intent :clean
    :label "intake assignment-1 (clean, no vulnerable person)"
    :request {:op :assignment/intake :subject "assignment-1"
              :patch {:id "assignment-1" :worker "Kita Sato"}}}
   {:tid "t2" :intent :clean :approval {:status :approved :by "op-1"}
    :label "assess jurisdiction for assignment-1 (human approves)"
    :request {:op :jurisdiction/assess :subject "assignment-1"}}
   {:tid "t3" :intent :clean :approval {:status :approved :by "op-1"}
    :label "dispatch assignment-1 (actuation -- human approves)"
    :request {:op :assignment/dispatch :subject "assignment-1"}}
   {:tid "t4" :intent :clean :approval {:status :approved :by "op-1"}
    :label "pay assignment-1 (actuation -- human approves)"
    :request {:op :assignment/pay :subject "assignment-1"}}

   {:tid "t5" :intent :clean
    :label "intake assignment-6 (vulnerable person, safeguarding verified)"
    :request {:op :assignment/intake :subject "assignment-6"
              :patch {:id "assignment-6" :worker "Chuo Yuki"}}}
   ;; Ordering violation, deliberately placed BEFORE t6: assignment-6 is
   ;; registered, safeguarded and in a jurisdiction that HAS a spec, so
   ;; the only thing wrong with dispatching it here is that its
   ;; jurisdiction has not been assessed yet -- which isolates
   ;; :evidence-incomplete to a single violation on a single row.
   {:tid "t5b" :intent :refusal
    :label "dispatch assignment-6 BEFORE its jurisdiction is assessed"
    :request {:op :assignment/dispatch :subject "assignment-6"}}
   {:tid "t6" :intent :clean :approval {:status :approved :by "op-1"}
    :label "assess jurisdiction for assignment-6 (human approves)"
    :request {:op :jurisdiction/assess :subject "assignment-6"}}
   {:tid "t7" :intent :clean :approval {:status :approved :by "op-1"}
    :label "dispatch assignment-6 (safeguarding verified -- passes)"
    :request {:op :assignment/dispatch :subject "assignment-6"}}
   {:tid "t7b" :intent :clean :approval {:status :approved :by "op-1"}
    :label "pay assignment-6 (actuation -- human approves)"
    :request {:op :assignment/pay :subject "assignment-6"}}

   {:tid "t8" :intent :refusal
    :label "assess assignment-2 -- jurisdiction ATL has no spec-basis"
    :request {:op :jurisdiction/assess :subject "assignment-2" :no-spec? true}}

   {:tid "t9" :intent :clean :approval {:status :approved :by "op-1"}
    :label "assess jurisdiction for assignment-3 (sets up payroll test)"
    :request {:op :jurisdiction/assess :subject "assignment-3"}}
   {:tid "t9b" :intent :clean :approval {:status :approved :by "op-1"}
    :label "dispatch assignment-3 (human approves)"
    :request {:op :assignment/dispatch :subject "assignment-3"}}
   {:tid "t10" :intent :refusal
    :label "pay assignment-3 -- claimed gross disagrees with kotoba.labor recompute"
    :request {:op :assignment/pay :subject "assignment-3"}}

   {:tid "t11" :intent :clean :approval {:status :approved :by "op-1"}
    :label "assess jurisdiction for assignment-4 (sets up registration test)"
    :request {:op :jurisdiction/assess :subject "assignment-4"}}
   {:tid "t12" :intent :refusal
    :label "dispatch assignment-4 -- household employer not registered"
    :request {:op :assignment/dispatch :subject "assignment-4"}}

   {:tid "t13" :intent :clean :approval {:status :approved :by "op-1"}
    :label "assess jurisdiction for assignment-5 (sets up safeguarding test)"
    :request {:op :jurisdiction/assess :subject "assignment-5"}}
   {:tid "t14" :intent :refusal
    :label "dispatch assignment-5 -- cares for a vulnerable person, no safeguarding check"
    :request {:op :assignment/dispatch :subject "assignment-5"}}

   {:tid "t15" :intent :refusal
    :label "dispatch assignment-1 AGAIN -- double dispatch"
    :request {:op :assignment/dispatch :subject "assignment-1"}}
   {:tid "t16" :intent :refusal
    :label "pay assignment-1 AGAIN -- double payroll posting"
    :request {:op :assignment/pay :subject "assignment-1"}}])

(def control-scenarios
  "Two CONTROL runs, each against its OWN fresh store so they cannot
  perturb the primary register state. They exist to prove the
  classifier discriminates: both produce a genuine refusal to write,
  and NEITHER is a governor refusal.

    phase-gate         -- phase 0 is read-only, so a governor-CLEAN
                          intake is still refused. The fact carries an
                          EMPTY :violations.
    approver-rejection -- the governor and phase both cleared the
                          proposal and the human declined. The fact
                          DOES carry a :violations entry
                          ({:rule :approver-rejected}) even though the
                          governor raised nothing."
  [{:key :phase-gate :tid "c1"
    :label "intake assignment-1 at phase 0 (read-only rollout phase)"
    :context {:actor-id "op-1" :actor-role :household-employer :phase 0}
    :request {:op :assignment/intake :subject "assignment-1"
              :patch {:id "assignment-1" :worker "Kita Sato"}}}
   {:key :approver-rejection :tid "c2"
    :label "assess jurisdiction for assignment-1, human operator declines"
    :context operator
    :approval {:status :rejected :by "op-2"}
    :request {:op :jurisdiction/assess :subject "assignment-1"}}])

;; ---------------------------------------------------------------------------
;; Approver attribution -- derived at render time, never assumed
;; ---------------------------------------------------------------------------

(def ^:private approver-key-re
  #"(?i)approv|signed[-_ ]?by|authoriz|sign-?off")

(defn- key-name [k] (if (keyword? k) (name k) (str k)))

(defn approver-shaped-keys
  "Keys of `m` that name an APPROVER. `:actor` is deliberately excluded:
  on a ledger fact `:actor` is the EXECUTING actor, not the human who
  approved the act."
  [m]
  (when (map? m)
    (->> (keys m)
         (remove #(= :actor %))
         (filter #(re-find approver-key-re (key-name %)))
         (sort-by key-name)
         vec)))

(defn attribution-report
  "Scan the SSoT that the run actually produced and report, PER EFFECT,
  whether the approving human's identity survived the commit.

  Derived entirely by scanning the persisted records for approver-shaped
  keys -- this namespace never hard-codes a claim about this repo."
  [db assignment-ids approved-effects]
  (let [probe (fn [effect records]
                {:effect effect
                 :n (count records)
                 :retained (->> records (mapcat approver-shaped-keys) distinct sort vec)})
        assessments (keep #(store/assessment-of db %) assignment-ids)]
    (->> [(probe :assessment/set (vec assessments))
          (probe :assignment/mark-dispatched (vec (store/dispatch-history db)))
          (probe :assignment/mark-paid (vec (store/payroll-history db)))]
         (map #(assoc % :approved? (contains? approved-effects (:effect %))))
         (filter #(pos? (:n %)))
         vec)))

;; ---------------------------------------------------------------------------
;; Model -- one full build
;; ---------------------------------------------------------------------------

(defn- step-rows
  "Fold the run results into one row per ledger fact, in ledger order."
  [results]
  (vec (for [{:keys [scenario new-facts state]} results
             f new-facts]
         {:label (:label scenario)
          :op (:op (:request scenario))
          :subject (:subject (:request scenario))
          :class (classify-fact f)
          :fact f
          :confidence (:confidence (:verdict state))})))

(defn run-console
  "Execute `scs` against a fresh seeded store and return the model the
  page is rendered from. Pure with respect to the filesystem."
  [scs]
  (let [db (store/seed-db)
        actor (op/build db)
        results (vec (for [sc scs]
                       {:scenario sc
                        :result (run-step! db actor (assoc sc :context (or (:context sc) operator)))}))
        results (mapv #(merge (:scenario %) {:scenario (:scenario %)}
                              (:result %)) results)
        rows (step-rows results)
        approved-effects (->> results
                              (filter :approval)
                              (filter #(= :approved (:status (:approval %))))
                              (keep #(get-in % [:state :record :effect]))
                              set)]
    {:db db
     :rows rows
     :ledger (vec (store/ledger db))
     :assignments (vec (store/all-assignments db))
     :dispatch (vec (store/dispatch-history db))
     :payroll (vec (store/payroll-history db))
     :attribution (attribution-report db
                                      (mapv :id (store/all-assignments db))
                                      approved-effects)
     :counts (frequencies (map :class rows))}))

(defn hard-holds
  "The HARD governor refusals in a model -- the rows the Domestic
  Employment Governor itself refused, excluding phase-gate holds and
  human approver rejections.

  Discriminator test (run before this renderer landed, and repeatable):
  re-running `run-console` over `(remove #(= :refusal (:intent %))
  scenarios)` drops this count to 0 while the committed rows remain,
  which is what makes the `-main` invariant below a real check rather
  than a tautology."
  [model]
  (filterv #(= :governor-refusal (:class %)) (:rows model)))

;; ---------------------------------------------------------------------------
;; Rendering
;; ---------------------------------------------------------------------------

(defn- cell
  "Render one table cell value. Never emits a literal `nil`."
  [v]
  (cond
    (nil? v) "—"
    (keyword? v) (str v)
    (coll? v) (if (seq v) (str/join ", " (map cell v)) "—")
    :else (str v)))

(def ^:private extra-rules
  "The few rules the DADS compat skin does not carry, emitted AFTER
  `dds.css` + `skin-css` so they win the cascade. A vector (not a map)
  because CSS is order-sensitive. Colours and type reference DADS
  custom properties only -- no raw hex."
  [[".rule"        {:font-family "var(--font-family-mono)"
                    :font-variant-numeric "tabular-nums"
                    :font-size ".8125rem"}]
   [".detail"      {:color "var(--color-neutral-solid-gray-700)"
                    :font-size ".875rem"}]
   ["td.amt"       {:text-align "right"}]
   [".summary"     {:display "flex" :gap "1.5rem" :flex-wrap "wrap"}]
   [".summary div" {:min-width "9rem"}]
   [".summary .n"  {:display "block" :font-size "1.75rem" :font-weight 700
                    :font-family "var(--font-family-mono)"
                    :font-variant-numeric "tabular-nums"
                    :color "var(--color-neutral-solid-gray-900)"}]
   [".summary .k"  {:font-size ".75rem" :text-transform "uppercase"
                    :letter-spacing ".04em"
                    :color "var(--color-neutral-solid-gray-600)"}]
   ["header.bar"   {:margin-bottom "1.5rem"}]
   ["header.bar h1" {:margin 0}]
   ["header.bar .badge" {:margin-left "auto"}]])

(def ^:private sheet-css
  "vendored DADS stylesheet + compat skin + this page's few extras."
  (str (skin/dds+skin) "\n" (css/css {:rules extra-rules})))

(defn- tag [class-kw]
  [:span {:class (str "tag " (class-css class-kw))} (class-label class-kw)])

(defn- summary-section [model]
  (let [c (:counts model)]
    [:section.card
     [:h2 "Run summary"]
     [:div.summary
      (for [[k label] [[:commit "committed"]
                       [:governor-refusal "governor holds (hard)"]
                       [:phase-gate "phase-gate holds"]
                       [:approver-rejection "approver rejections"]]]
        [:div [:span.n (str (get c k 0))] [:span.k label]])]]))

(defn- scenario-section [model]
  [:section.card
   [:h2 (str "Operations (" (count (:rows model)) " ledger facts)")]
   [:table
    [:thead [:tr [:th "Op"] [:th "Subject"] [:th "Scenario"]
             [:th "Outcome"] [:th.amt "Confidence"]]]
    [:tbody
     (for [r (:rows model)]
       [:tr [:td.rule (cell (:op r))]
        [:td (cell (:subject r))]
        [:td.detail (cell (:label r))]
        [:td (tag (:class r))]
        [:td.amt (cell (:confidence r))]])]]])

(defn- holds-section [model]
  (let [hs (hard-holds model)]
    [:section.card
     [:h2 (str "HARD governor holds — " (count hs) " refusals no approver can override")]
     [:p.note
      "These are refusals by the Domestic Employment Governor itself. They are "
      "classified on the ledger fact's own type, not on the presence of a "
      ":violations entry — see the two control runs below, which also refuse to "
      "write but are NOT governor refusals."]
     [:table
      [:thead [:tr [:th "Op"] [:th "Subject"] [:th "Rule"] [:th "Governor's stated reason"]]]
      [:tbody
       (for [h hs
             v (:violations (:fact h))]
         [:tr [:td.rule (cell (:op h))]
          [:td (cell (:subject h))]
          [:td.rule [:span.critical (cell (:rule v))]]
          [:td.detail (cell (:detail v))]])]]]))

(defn- controls-section [controls]
  [:section.card
   [:h2 "Control runs — refusals that are NOT governor refusals"]
   [:p.note
    "Each control runs against its own fresh store so it cannot perturb the "
    "registers above. Both refuse to write. Neither is counted as a governor "
    "hold. Note that the approver rejection carries a :violations entry while "
    "the phase-gate hold carries none — which is why :violations alone cannot "
    "be the discriminator."]
   [:table
    [:thead [:tr [:th "Control"] [:th "Scenario"] [:th "Outcome"]
             [:th "Fact :t"] [:th ":phase-reason"] [:th ":violations"]]]
    [:tbody
     (for [c controls]
       [:tr [:td.rule (cell (:key c))]
        [:td.detail (cell (:label c))]
        [:td (tag (:class c))]
        [:td.rule (cell (:t (:fact c)))]
        [:td.rule (cell (:phase-reason (:fact c)))]
        [:td.rule (cell (mapv :rule (:violations (:fact c))))]])]]])

(defn- assignments-section [model]
  [:section.card
   [:h2 (str "Assignment register (" (count (:assignments model)) ")")]
   [:table
    [:thead [:tr [:th "ID"] [:th "Worker"] [:th "Household"] [:th "Role"]
             [:th.amt "Rate"] [:th.amt "Hours"] [:th.amt "Claimed gross"]
             [:th "Registered?"] [:th "Vulnerable?"] [:th "Safeguarded?"]
             [:th "Dispatch no."] [:th "Payroll no."]]]
    [:tbody
     (for [a (:assignments model)]
       [:tr [:td.rule (cell (:id a))]
        [:td (cell (:worker a))]
        [:td (cell (:household a))]
        [:td (cell (:role a))]
        [:td.amt (cell (:rate a))]
        [:td.amt (cell (:hours-worked a))]
        [:td.amt (cell (:claimed-gross a))]
        [:td [:span {:class (if (:household-employment-registered? a) "ok" "critical")}
              (cell (:household-employment-registered? a))]]
        [:td (cell (:involves-vulnerable-person? a))]
        [:td [:span {:class (if (:safeguarding-check-verified? a) "ok" "muted")}
              (cell (:safeguarding-check-verified? a))]]
        [:td.rule (cell (:dispatch-number a))]
        [:td.rule (cell (:payroll-number a))]])]]])

(defn- register-section [title records]
  [:section.card
   [:h2 (str title " (" (count records) ")")]
   [:table
    [:thead [:tr [:th "Record ID"] [:th "Kind"] [:th "Assignment"]
             [:th "Jurisdiction"] [:th "Immutable"]]]
    [:tbody
     (for [r records]
       [:tr [:td.rule (cell (get r "record_id"))]
        [:td (cell (get r "kind"))]
        [:td.rule (cell (get r "assignment_id"))]
        [:td (cell (get r "jurisdiction"))]
        [:td (cell (get r "immutable"))]])]]])

(defn- attribution-section [model]
  [:section.card
   [:h2 "Approver attribution — measured on this run"]
   [:p.note
    "Derived at render time by scanning each committed record for an "
    "approver-shaped key. The ledger's :actor field is excluded, because it "
    "names the EXECUTING actor rather than the human who approved."]
   [:table
    [:thead [:tr [:th "Effect"] [:th.amt "Records"] [:th "Human-approved on this run"]
             [:th "Approver key retained"]]]
    [:tbody
     (for [a (:attribution model)]
       [:tr [:td.rule (cell (:effect a))]
        [:td.amt (cell (:n a))]
        [:td (cell (:approved? a))]
        [:td (if (seq (:retained a))
               [:span.ok (cell (:retained a))]
               (if (:approved? a)
                 [:span.critical "not retained"]
                 [:span.muted "—"]))]])]]
   (let [lossy (filterv #(and (:approved? %) (empty? (:retained %))) (:attribution model))]
     (when (seq lossy)
       [:p.note
        [:span.critical "DISCLOSED DEFECT"]
        (str " — " (count lossy)
             " effect(s) committed after an explicit human approval do not persist "
             "the approver's identity: "
             (str/join ", " (map (comp str :effect) lossy))
             ". The approval is recorded in the run's audit channel, but the "
             "record written to the SSoT drops it, so the register alone cannot "
             "answer \"who authorised this?\". This is reported, not patched: "
             "changing the governor or the store is out of scope for a rendering "
             "task.")]))])

(defn- ledger-section [model]
  [:section.card
   [:h2 (str "Audit ledger (" (count (:ledger model)) " append-only facts)")]
   [:table
    [:thead [:tr [:th "#"] [:th ":t"] [:th "Op"] [:th "Subject"]
             [:th "Disposition"] [:th "Basis"]]]
    [:tbody
     (for [[i f] (map-indexed vector (:ledger model))]
       [:tr [:td.amt (str i)]
        [:td.rule (cell (:t f))]
        [:td.rule (cell (:op f))]
        [:td (cell (:subject f))]
        [:td (cell (:disposition f))]
        [:td.detail (cell (:basis f))]])]]])

(defn page
  "Render the full console document from a model + control results."
  [model controls]
  (html/html5
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "cloud-itonami · domestic-employment · operator console"]
     [:hiccup/raw (html/->html [:style [:hiccup/raw sheet-css]])]]
    [:body
     [:header.bar
      [:h1 "Domestic Employment — Operator Console"]
      [:span.badge "generated from a real actor run · governor-gated"]]
     [:main
      [:section.card
       [:p.note
        "Every row below was produced by executing this repository's own actor "
        "(domesticops.operation/build over domesticops.store/seed-db, driven "
        "through langgraph.graph/run*) at build time. No value on this page is "
        "hand-authored. Rebuild with "
        [:span.rule "clojure -M:dev:render-html"] "."]]
      (summary-section model)
      (holds-section model)
      (scenario-section model)
      (controls-section controls)
      (assignments-section model)
      (register-section "Mission-dispatch register" (:dispatch model))
      (register-section "Payroll-posting register" (:payroll model))
      (attribution-section model)
      (ledger-section model)]]]))

;; ---------------------------------------------------------------------------
;; Build entry point
;; ---------------------------------------------------------------------------

(defn run-controls
  "Run each control scenario against its OWN fresh store."
  []
  (vec (for [c control-scenarios]
         (let [db (store/seed-db)
               actor (op/build db)
               {:keys [new-facts]} (run-step! db actor c)
               f (last new-facts)]
           (assoc c :fact f :class (classify-fact f))))))

(defn build
  "Produce the console HTML string, or throw if the run did not exercise
  at least one HARD governor hold."
  []
  (let [model (run-console scenarios)
        hs (hard-holds model)]
    (when (zero? (count hs))
      (throw (ex-info (str "refusing to write operator-console.html: the actor run "
                           "produced ZERO hard governor holds. A console that shows "
                           "no genuine refusal cannot evidence that the governor "
                           "gates anything.")
                      {:counts (:counts model)})))
    {:html (page model (run-controls))
     :model model
     :hard-holds hs}))

(defn -main
  "Write docs/samples/operator-console.html (or the path given as the
  first argument) from a real actor run."
  [& [out]]
  (let [out (io/file (or out "docs/samples/operator-console.html"))
        {:keys [html model hard-holds]} (build)]
    (io/make-parents out)
    (spit out html)
    (println (str "wrote " out " (" (count (.getBytes ^String html "UTF-8")) " bytes)"))
    (println (str "  ledger facts     : " (count (:ledger model))))
    (println (str "  hard governor holds: " (count hard-holds)))
    (doseq [h hard-holds]
      (println (str "    - " (:op h) " " (:subject h) " -> "
                    (str/join "," (map (comp str :rule) (:violations (:fact h)))))))
    (println (str "  counts           : " (pr-str (:counts model))))
    (doseq [a (:attribution model)]
      (println (str "  attribution " (:effect a)
                    " n=" (:n a) " approved=" (:approved? a)
                    " retained=" (pr-str (:retained a)))))))
