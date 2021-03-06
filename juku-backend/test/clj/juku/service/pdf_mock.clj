(ns juku.service.pdf-mock
  (:require [midje.sweet :refer :all]
            [clj-time.core :as time]
            [clj-time.format :as timef]
            [juku.service.pdf :as pdf]))

(def ^:dynamic *mock-pdf*)

(def original-muodosta-pdf pdf/muodosta-pdf)

(defn muodosta-pdf-mock [osat]
  (set! *mock-pdf* osat)
  (original-muodosta-pdf osat))

(defmacro with-mock-pdf [& body]
  `(with-redefs [pdf/muodosta-pdf muodosta-pdf-mock]
     (binding [*mock-pdf* {}] ~@body)))

(def today (timef/unparse-local-date (timef/formatter "d.M.yyyy") (time/today)))

(defn assert-otsikko
  ([teksti diaarinumero] (assert-otsikko teksti today diaarinumero))
  ([teksti pvm diaarinumero]
    (fact "Otsikko on oikein"
          (:otsikko *mock-pdf*) => {:teksti teksti
                                    :paivays pvm
                                    :diaarinumero diaarinumero})))