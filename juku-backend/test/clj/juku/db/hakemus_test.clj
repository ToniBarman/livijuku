(ns juku.db.hakemus_test
  (:require [midje.sweet :refer :all]
            [clj-time.core :as t]
            [juku.service.hakemus :as h]))

(defn find-by-id [id] (fn [m] (= (:id m) id)))

(fact "Hakemuksen tallentamisen testi"
  (let [organisaatioid 1M
        hakemus {:vuosi 2015 :hakemustyyppitunnus "AH0"
                 :organisaatioid organisaatioid
                 :hakuaika {:alkupvm (t/local-date 2014 6 1)
                            :loppupvm (t/local-date 2014 12 1)}}

        id (h/add-hakemus! hakemus)]

    (first (filter (find-by-id id) (h/find-organisaation-hakemukset organisaatioid)))
      => (-> hakemus (assoc :id id) (assoc :hakemustilatunnus "K"))))

(fact
  (let [organisaatioid 1
        hakemus {:vuosi 2015 :hakemustyyppitunnus "AH0" :organisaatioid organisaatioid
                 :hakuaika {:alkupvm (t/local-date 2014 6 1)
                            :loppupvm (t/local-date 2014 12 1)}}

        id (h/add-hakemus! hakemus)
        avustuskohde {:hakemusid id, :avustuskohdelajitunnus "PSA-1", :haettavaavustus 1, :omarahoitus 1}]

      (h/save-avustuskohteet![avustuskohde])
      (h/find-avustuskohteet-by-hakemusid id) => [avustuskohde]
    ))