(ns juku.service.hakemus
  (:require [juku.db.yesql-patch :as sql]
            [juku.service.user :as user]
            [juku.db.database :refer [db with-transaction]]
            [juku.db.coerce :as coerce]
            [juku.db.sql :as dml]
            [juku.service.organisaatio :as o]
            [common.string :as xstr]
            [clojure.string :as str]
            [clj-time.core :as time]
            [schema.coerce :as scoerce]
            [clojure.java.io :as io]
            [juku.service.pdf :as pdf]
            [juku.schema.hakemus :refer :all]
            [ring.util.http-response :as r]
            [common.collection :as c])
  (:import (org.joda.time LocalDate)))

; *** Hakemukseen liittyvät kyselyt ***
(sql/defqueries "hakemus.sql")

; *** Hakemus skeemaan liittyvät konversiot tietokannan tietotyypeistä ***
(def coerce-hakemus (scoerce/coercer Hakemus coerce/db-coercion-matcher))
(def coerce-hakemus+ (scoerce/coercer Hakemus+ coerce/db-coercion-matcher))
(def coerce-hakemus-suunnitelma (scoerce/coercer HakemusSuunnitelma coerce/db-coercion-matcher))

(def coerce-avustuskohde (scoerce/coercer Avustuskohde coerce/db-coercion-matcher))

; *** Virheviestit tietokannan rajoitteista ***
(def constraint-errors
  {:hakemus_hakemustyyppi_fk {:http-response r/not-found :message "Hakemustyyppiä {hakemustyyppitunnus} ei ole olemassa."}
   :hakemus_organisaatio_fk {:http-response r/not-found :message "Hakemuksen organisaatiota {organisaatioid} ei ole olemassa."}
   :hakemus_kasittelija_fk {:http-response r/not-found :message "Hakemuksen käsittelijää {kasittelija} ei ole olemassa."}
   :hakemus_hakemuskausi_fk {:http-response r/not-found :message "Hakemuskautta {vuosi} ei ole olemassa."}

   :avustuskohde_pk {:http-response r/bad-request :message "Avustuskohde {avustuskohdeluokkatunnus}-{avustuskohdelajitunnus} on jo olemassa hakemuksella (id = {hakemusid})."}
   :avustuskohde_hakemus_fk {:http-response r/not-found :message "Avustuskohteen {avustuskohdeluokkatunnus}-{avustuskohdelajitunnus} hakemusta (id = {hakemusid}) ei ole olemassa."}
   :avustuskohde_aklaji_fk {:http-response r/not-found :message "Avustuskohdelajia {avustuskohdeluokkatunnus}-{avustuskohdelajitunnus} ei ole olemassa."}})

; *** Hakemukseen ja sen sisältöön liittyvät palvelut ***

(defn find-organisaation-hakemukset [organisaatioid]
  (map (comp coerce-hakemus coerce/row->object)
    (select-organisaation-hakemukset {:organisaatioid organisaatioid})))

(defn find-all-hakemukset []
  (map (comp coerce-hakemus coerce/row->object)
       (select-all-hakemukset)))

(defn hakemukset-group-by-hakemuskausi [hakemukset]
  (let [vuosittain (group-by :vuosi hakemukset)]
    (reduce (fn [result [key value]] (conj result {:vuosi key :hakemukset value}))
            '() vuosittain)))

(defn find-organisaation-hakemukset-vuosittain [organisaatioid]
  (hakemukset-group-by-hakemuskausi (find-organisaation-hakemukset organisaatioid)))

(defn find-kayttajan-hakemukset-vuosittain []
  (find-organisaation-hakemukset-vuosittain (:organisaatioid user/*current-user*)))

(defn find-hakemukset-vuosittain []
  (hakemukset-group-by-hakemuskausi (find-all-hakemukset)))

(defn get-hakemus-by-id [hakemusid]
  (-> (select-hakemus {:hakemusid hakemusid})
      (c/single-result-required ::hakemus-not-found {:hakemusid hakemusid} (str "Hakemusta " hakemusid " ei ole olemassa."))
      coerce/row->object
      coerce-hakemus+))

(defn find-avustuskohteet-by-hakemusid [hakemusid]
  (map coerce-avustuskohde (select-avustuskohteet {:hakemusid hakemusid})))

(defn find-hakemussuunnitelmat [vuosi hakemustyyppitunnus]
  (map (comp coerce-hakemus-suunnitelma coerce/row->object)
       (select-hakemussuunnitelmat {:vuosi vuosi :hakemustyyppitunnus hakemustyyppitunnus})))

(defn add-hakemus! [hakemus]
  (:id (dml/insert-with-id db "hakemus"
                           (-> hakemus
                               coerce/object->row
                               coerce/localdate->sql-date)
                           constraint-errors hakemus)))

(defn add-avustuskohde! [avustuskohde]
  (:id (dml/insert db "avustuskohde"
                           (-> avustuskohde
                               coerce/object->row
                               coerce/localdate->sql-date)
                           constraint-errors avustuskohde)))

(defn save-avustuskohde! [avustuskohde]
  (if (= (update-avustuskohde! avustuskohde) 0)
    (add-avustuskohde! avustuskohde)))

(defn save-avustuskohteet! [avustuskohteet]
  (with-transaction
    (doseq [avustuskohde avustuskohteet]
      (save-avustuskohde! avustuskohde))))

(defn- update-hakemus-by-id [hakemus hakemusid]
  (dml/update-where-id db "hakemus" hakemus hakemusid))

;; TODO probably does not work for over 4000 byte strings
(defn save-hakemus-selite! [hakemusid selite]
  (update-hakemus-by-id {:selite selite} hakemusid))

(defn save-hakemus-suunniteltuavustus! [hakemusid suunniteltuavustus]
  (update-hakemus-by-id {:suunniteltuavustus suunniteltuavustus} hakemusid))

(defn save-hakemus-kasittelija! [hakemusid kasittelija]
  (update-hakemus-by-id {:kasittelija kasittelija} hakemusid))

(defn laheta-hakemus! [hakemusid]
  (update-hakemustila! {:hakemusid hakemusid :hakemustilatunnus "V"}))

(defn tarkasta-hakemus! [hakemusid]
  (update-hakemustila! {:hakemusid hakemusid :hakemustilatunnus "T"}))

(defn taydennyspyynto! [hakemusid]
  (update-hakemustila! {:hakemusid hakemusid :hakemustilatunnus "T0"}))

(defn laheta-taydennys! [hakemusid]
  (update-hakemustila! {:hakemusid hakemusid :hakemustilatunnus "TV"}))

(defn avustuskohde-luokittelu []
  (let [luokat (select-avustuskohdeluokat)
        lajit (select-avustuskohdelajit)
        lajit-group-by-luokka (group-by :avustuskohdeluokkatunnus lajit)
        assoc-avustuskohdelajit (fn [luokka] (assoc luokka :avustuskohdelajit (get lajit-group-by-luokka (:tunnus luokka))))]
    (map assoc-avustuskohdelajit luokat)))

(defn hakemus-pdf [hakemusid]
  (let [vireillepvm-txt (.toString ^LocalDate (time/today) "d.M.y")
        hakemus (get-hakemus-by-id hakemusid)
        organisaatio (o/find-organisaatio (:organisaatioid hakemus))
        avustuskohteet (find-avustuskohteet-by-hakemusid hakemusid)

        total-haettavaavustus (reduce + 0 (map :haettavaavustus avustuskohteet))
        total-omarahoitus (reduce + 0 (map :omarahoitus avustuskohteet))

        avustuskohde-template "\t{avustuskohdelajitunnus},\t{haettavaavustus} euroa"
        avustuskohteet-section (str/join "\n" (map (partial xstr/interpolate avustuskohde-template)
                                                   (filter (c/predicate > :haettavaavustus 0) avustuskohteet)))

        template (slurp (io/reader (io/resource "pdf-sisalto/templates/hakemus.txt")))]

    (pdf/muodosta-pdf
      {:otsikko {:teksti "Valtionavustushakemus" :paivays vireillepvm-txt :diaarinumero (:diaarinumero hakemus)}
       :teksti (xstr/interpolate template
                                 {:organisaatio-nimi (:nimi organisaatio)
                                  :vireillepvm vireillepvm-txt
                                  :vuosi (:vuosi hakemus)
                                  :avustuskohteet avustuskohteet-section
                                  :haettuavustus total-haettavaavustus
                                  :omarahoitus total-omarahoitus})
       :footer "Footer"})))