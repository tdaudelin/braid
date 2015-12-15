(ns chat.server.search
  (:require [datomic.api :as d]
            [clojure.string :as string]
            [clojure.set :refer [intersection]]
            [instaparse.core :as insta]
            [chat.server.db :as db]))

; TODO: some way to search for a tag with spaces in it?
(def query-parser
  (insta/parser
    "S ::= ( TAG / DOT ) *
    DOT ::= #'(?s).'
    <ws> ::= #'\\s*'
    TAG ::= <'#'> #'[^ ]+'
    "))

(defn squash
  [s]
  (if (string/blank? s)
   ""
    (-> s
        (string/replace #"\s+" " ")
        (string/replace #"^\s+" ""))))

(defn parse-query
  [txt]
  (let [parsed (query-parser txt)
        text-query (->> parsed
                        (insta/transform
                          {:DOT str
                           :TAG (constantly "")})
                        rest
                        (apply str)
                        squash)
        tag-query (->> parsed
                       (insta/transform
                         {:DOT (constantly nil)
                          :TAG identity})
                       rest
                       (remove nil?))]
    {:text text-query
     :tags tag-query}))

(defn search-threads-as
  [user-id query]
  ; TODO: pagination?
  ; TODO: consistent order for results
  (let [{:keys [text tags]} (parse-query query)
        search-db (d/db db/*conn*)
        tag-search (when (seq tags)
                     (set (d/q '[:find [?t-id ...]
                                 :in $ [?tag-name ...]
                                 :where
                                 [?tag :tag/name ?tag-name]
                                 [?t :thread/tag ?tag]
                                 [?t :thread/id ?t-id]]
                               search-db tags)))
        text-search (when-not (string/blank? text)
                      (set (d/q '[:find [?t-id ...]
                                  :in $ ?txt
                                  :where
                                  [(fulltext $ :message/content ?txt) [[?m]]]
                                  [?m :message/thread ?t]
                                  [?t :thread/id ?t-id]]
                                (d/db db/*conn*) text)))]
    (->> (if (every? some? [text-search tag-search])
           (intersection text-search tag-search)
           (first (remove nil? [text-search tag-search])))
         (into #{} (filter (partial db/user-can-see-thread? user-id))))))