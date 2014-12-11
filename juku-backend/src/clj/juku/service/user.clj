(ns juku.service.user
  (:require [yesql.core :as sql]
            [clojure.java.jdbc :as jdbc]
            [juku.db.database :refer [db]]
            [juku.db.coerce :as coerce]
            [schema.coerce :as scoerce]
            [juku.schema.user :as s]
            [clojure.string :as str]
            [common.map :as m]))

(sql/defqueries "user.sql" {:connection db})

(def ^:dynamic *current-user*)

(defn user-fullname [user]
  (str (:etunimi user) " " (:sukunimi user)))

(defn with-user*
  [user f]
  (binding [*current-user* user] (f)))

(defmacro with-user [user & body]
  `(with-user* ~user (fn [] ~@body)))

(defn user-coercien-matcher [schema]
  (or
    (coerce/number->boolean-matcher schema)))

(def coerce-user (scoerce/coercer s/DbUser user-coercien-matcher))

(defn find-user [tunnus]
  (coerce-user(m/dissoc-if-nil(first (select-user {:tunnus tunnus})) :nimi :etunimi :sukuni)))