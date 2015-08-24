(ns juku.rest-api.user
  (:require [compojure.api.sweet :refer :all]
            [juku.service.user :as service]
            [juku.schema.user :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defroutes* user-routes
    (GET* "/user" []
          :return User+Privileges
          :summary "Hae nykyisen käyttäjän tiedot."
          (ok (service/current-user+updatekirjautumisaika!)))
    (PUT* "/user" []
          :return User+Privileges
          :body [user EditUser]
          :summary "Päivitä nykyisen käyttäjän tiedot."
          (ok (service/save-user! user)))
    (GET* "/organisaatio/:organisaatioid/users" []
          :return [User+Roles]
          :path-params [organisaatioid :- Long]
          :summary "Hae organisaation kaikki käyttäjät."
          (ok (service/find-users-by-organization organisaatioid)))
    (GET* "/users" []
          :return [User+Roles]
          :summary "Hae kaikkien käyttäjien tiedot."
          (ok (service/find-all-users))))