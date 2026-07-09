(ns domesticops.facts
  "Per-jurisdiction household-employer-registration AND vulnerable-
  person-safeguarding regulatory catalog -- the G2-style spec-basis
  table the Domestic Employment Governor checks every `:jurisdiction/
  assess` proposal against ('did the advisor cite an OFFICIAL public
  source for this jurisdiction's requirements, or did it invent
  one?').

  This blueprint's own text (docs/business-model.md's own Offer:
  'employment contracts... payroll with documented deductions' and
  its Trust Controls: 'safety-critical actions (near a child/elder,
  agents, private rooms) require human sign-off') names two real,
  distinct regulatory concerns: the general household-employer-
  registration framework a household must comply with before it may
  lawfully employ and pay a domestic worker (independent of whether
  the worker cares for a vulnerable person), and a SEPARATE
  vulnerable-person-safeguarding regime specifically requiring a
  background/safeguarding check before a worker is assigned to care
  for a child or elder (independent of whether the household-employer
  registration itself is in order -- a fully-registered household can
  still assign an unchecked worker to child care, and a properly
  safeguarding-checked worker can still be paid by an unregistered
  household). Each jurisdiction entry below therefore cites BOTH the
  general household-employer-registration law AND a SEPARATE
  vulnerable-person-safeguarding law.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries. Like
  `libraryops`/9101's own conservation-standards sub-citation, ALL
  FOUR seeded jurisdictions actually have a real vulnerable-person-
  safeguarding sub-citation here, reported honestly (a full-coverage
  sub-citation, matching `quarryops`/0810's own blast-safety and
  `agronomyops`/0162's own water-buffer full coverage rather than
  `hospitalityops`/5510's own honest single-jurisdiction gap).")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  intake/contract/timesheet-record evidence set (PLUS a safeguarding-
  check record for every seeded jurisdiction); `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:jurisdiction/assess` proposal can commit.
  `:safeguarding-owner-authority` / `:safeguarding-legal-basis` /
  `:safeguarding-provenance` are the SEPARATE vulnerable-person-
  safeguarding citation the governor's `vulnerable-person-
  safeguarding-check-missing?` check is grounded in."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare, MHLW) / 日本年金機構"
          :legal-basis "労働保険の保険料の徴収等に関する法律 (家事使用人特例) 及び 健康保険法/厚生年金保険法"
          :national-spec "家事使用人を雇用する世帯主の労働保険・社会保険手続きガイドライン"
          :provenance "https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/koyou_roudou/roudoukijun/kaigai02.html"
          :required-evidence ["受入記録 (intake record)"
                              "雇用契約記録 (contract record)"
                              "勤務記録 (timesheet record)"
                              "身元確認記録 (safeguarding-check record)"]
          :safeguarding-owner-authority "こども家庭庁 (Children and Families Agency)"
          :safeguarding-legal-basis "児童福祉法 (Child Welfare Act) 及び 日本版DBS制度 (こども性暴力防止法)"
          :safeguarding-provenance "https://www.cfa.go.jp/policies/kodomo-kkc/"}
   "USA" {:name "United States"
          :owner-authority "Internal Revenue Service (IRS) Household Employment"
          :legal-basis "IRS Schedule H (Household Employment Taxes), 26 U.S.C. §3510"
          :national-spec "IRS/state household-employer registration and 'nanny tax' withholding rules"
          :provenance "https://www.irs.gov/forms-pubs/about-schedule-h-form-1040"
          :required-evidence ["Intake record"
                              "Contract record"
                              "Timesheet record"
                              "Safeguarding-check record"]
          :safeguarding-owner-authority "State child-care licensing agencies / National Background Check Program"
          :safeguarding-legal-basis "State in-home child/elder-care background-check statutes"
          :safeguarding-provenance "https://www.acf.hhs.gov/occ/programs/national-background-check-program"}
   "GBR" {:name "United Kingdom"
          :owner-authority "HM Revenue and Customs (HMRC), PAYE for Employers"
          :legal-basis "Income Tax (Earnings and Pensions) Act 2003 (household-employer PAYE registration)"
          :national-spec "HMRC household-employer PAYE and National Minimum Wage guidance"
          :provenance "https://www.gov.uk/paye-for-employers"
          :required-evidence ["Intake record"
                              "Contract record"
                              "Timesheet record"
                              "Safeguarding-check record"]
          :safeguarding-owner-authority "Disclosure and Barring Service (DBS)"
          :safeguarding-legal-basis "Safeguarding Vulnerable Groups Act 2006 (regulated activity with children/vulnerable adults)"
          :safeguarding-provenance "https://www.gov.uk/government/organisations/disclosure-and-barring-service"}
   "DEU" {:name "Germany"
          :owner-authority "Minijob-Zentrale (Deutsche Rentenversicherung Knappschaft-Bahn-See)"
          :legal-basis "Haushaltsscheckverfahren, §28a SGB IV (Sozialgesetzbuch IV)"
          :national-spec "Minijob-Zentrale Haushaltsscheck-Meldeverfahren"
          :provenance "https://www.minijob-zentrale.de/DE/03_arbeitgeber/02_haushaltsscheckverfahren/haushaltsscheckverfahren_node.html"
          :required-evidence ["Aufnahmeprotokoll (intake record)"
                              "Vertragsprotokoll (contract record)"
                              "Arbeitszeitprotokoll (timesheet record)"
                              "Führungszeugnisnachweis (safeguarding-check record)"]
          :safeguarding-owner-authority "Bundesamt für Justiz / Jugendämter (youth welfare offices)"
          :safeguarding-legal-basis "§72a SGB VIII (erweitertes Führungszeugnis)"
          :safeguarding-provenance "https://www.bundesjustizamt.de/DE/Themen/Buergerdienste/Fuehrungszeugnis/Fuehrungszeugnis_node.html"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch a
  mission or post payroll on that basis."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-9700 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `domesticops.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

(defn safeguarding-spec-basis
  "The jurisdiction's vulnerable-person-safeguarding requirement map,
  or nil -- nil means this jurisdiction has NO formal statutory
  safeguarding regime this catalog is aware of. In this R0 catalog all
  four seeded jurisdictions actually have one, reported honestly (a
  full-coverage sub-citation, matching `quarryops`/0810's own blast-
  safety and `agronomyops`/0162's own water-buffer full coverage)."
  [iso3]
  (when-let [sb (spec-basis iso3)]
    (when (:safeguarding-owner-authority sb)
      (select-keys sb [:safeguarding-owner-authority :safeguarding-legal-basis :safeguarding-provenance]))))
