(ns firedraft.routes.core-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [ring.mock.request :as mock]
   [firedraft.routes.core :refer [app]]
   [firedraft.config :as config]
   [firedraft.middleware.formats :as mw.formats]
   [muuntaja.core :as m]
   [mount.core :as mount]))

(defn parse-json [body]
  (m/decode mw.formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'firedraft.config/env
                 #'firedraft.routes.core/app-routes)
    (f)))

(deftest test-app
  (testing "main route"
    (let [response ((app) (mock/request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (mock/request :get "/invalid"))]
      (is (= 404 (:status response))))))
