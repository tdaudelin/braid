(ns chat.test.server.search
  (:require [clojure.test :refer :all]
            [chat.server.db :as db]
            [chat.server.search :as search]))

(use-fixtures :each
              (fn [t]
                (binding [db/*uri* "datomic:mem://chat-test"]
                  (db/init!)
                  (db/with-conn (t))
                  (datomic.api/delete-database db/*uri*))))

(deftest searching-threads
  (let [user-1 (db/create-user! {:id (db/uuid)
                                 :email "foo@bar.com"
                                 :password "foobar"
                                 :avatar ""})
        user-2 (db/create-user! {:id (db/uuid)
                                 :email "bar@foo.com"
                                 :password "foobar"
                                 :avatar ""})
        group-1 (db/create-group! {:name "group1" :id (db/uuid)})
        group-2 (db/create-group! {:name "group2" :id (db/uuid)})
        tag-1 (db/create-tag! {:id (db/uuid) :name "tag1" :group-id (group-1 :id)})
        tag-2 (db/create-tag! {:id (db/uuid) :name "tag2" :group-id (group-2 :id)})
        thread-1-id (db/uuid)
        thread-2-id (db/uuid)
        thread-3-id (db/uuid)
        thread-4-id (db/uuid)]

    (db/user-add-to-group! (user-1 :id) (group-1 :id))
    (db/user-subscribe-to-tag! (user-1 :id) (tag-1 :id))

    (db/user-add-to-group! (user-2 :id) (group-2 :id))
    (db/user-subscribe-to-tag! (user-2 :id) (tag-2 :id))

    ; this thread should be visible to user 1
    (db/create-message! {:thread-id thread-1-id :id (db/uuid)
                         :content "Hello world" :user-id (user-1 :id)
                         :created-at (java.util.Date.)})
    (db/create-message! {:thread-id thread-1-id :id (db/uuid)
                         :content "Hey world" :user-id (user-2 :id)
                         :created-at (java.util.Date.)})

    ; this thread should be visible to user 1
    (db/create-message! {:thread-id thread-2-id :id (db/uuid)
                         :content "Goodbye World" :user-id (user-2 :id)
                         :created-at (java.util.Date.)})
    (db/thread-add-tag! thread-2-id (tag-1 :id))

    ; this thread should not be visible to user 1
    (db/create-message! {:thread-id thread-3-id :id (db/uuid)
                         :content "Hello world" :user-id (user-2 :id)
                         :created-at (java.util.Date.)})
    (db/thread-add-tag! thread-3-id (tag-2 :id))

    ; this thread should not be visible to user 1
    (db/create-message! {:thread-id thread-4-id :id (db/uuid)
                         :content "Something else" :user-id (user-2 :id)
                         :created-at (java.util.Date.)})
    (db/thread-add-tag! thread-4-id (tag-2 :id))

    (testing "user can seach and see threads"
      (is (= #{thread-1-id}
             (search/search-threads-as (user-1 :id) "hello")
             (search/search-threads-as (user-1 :id) "HELLO")))
      (is (= #{thread-1-id thread-2-id}
             (search/search-threads-as (user-1 :id) "world")))
      (is (= #{} (search/search-threads-as (user-1 :id) "something"))))))
