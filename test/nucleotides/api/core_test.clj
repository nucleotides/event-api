(ns nucleotides.api.core-test
  (:require [clojure.test       :refer :all]
            [ring.mock.request  :as mock]
            [clojure.data.json  :as json]

            [helper.event          :refer :all]
            [helper.fixture        :as fix]
            [helper.http-response  :as resp]
            [helper.database       :as db]
            [helper.image          :as image]

            [nucleotides.api.benchmarks-test :as bench-test]
            [nucleotides.api.tasks-test      :as task-test]

            [nucleotides.database.connection  :as con]
            [nucleotides.api.middleware       :as md]
            [nucleotides.api.core             :as app]))

(defn http-request
  "Create a mock request to the API"
  [{:keys [method url params body content] :or {params {}}}]
  (-> (mock/request method url params)
      (mock/body body)
      (mock/content-type content)
      ((md/middleware (app/api {:connection (con/create-connection)})))))

(defn test-app-response [{:keys [db-tests response-tests fixtures keep-db?] :as m}]
  (if (not keep-db?)
    (do
      (db/empty-database)
      (apply fix/load-fixture (concat fix/base-fixtures fixtures))))
  (let [response (http-request m)]
    (dorun
      (for [t response-tests]
        (t response)))
    (dorun
      (for [[table length] db-tests]
        (is (= length (db/table-length table)))))))


(defn test-get-event [{:keys [event-id fixtures files]}]
  (test-app-response
    {:method          :get
     :url             (str "/events/" event-id)
     :fixtures        fixtures
     :response-tests  [resp/is-ok-response
                       resp/is-not-empty-body
                       (resp/does-http-body-contain [:task :success :created_at :metrics :id :files])
                       (resp/contains-file-entries  [:files] (map resp/file-entry files))]}))


(defn test-show-tasks [{:keys [fixtures expected]}]
  (test-app-response
    {:method          :get
     :url             "/tasks/show.json"
     :fixtures        fixtures
     :response-tests  [resp/is-ok-response
                       #(is (= (sort (json/read-str (:body %))) (sort expected)))]}))


(defn test-get-task [{:keys [task-id fixtures files events]}]
  (test-app-response
    {:method          :get
     :url             (str "/tasks/" task-id)
     :fixtures        fixtures
     :response-tests  [resp/is-ok-response
                       image/has-image-metadata
                       task-test/contains-task-entries
                       (resp/contains-file-entries  [:inputs] (map resp/file-entry files))
                       (resp/contains-event-entries [:events] events)]}))


(deftest app

  (testing "GET /tasks/show.json"

    (testing "getting all incomplete tasks"
      (test-show-tasks {:expected [1 3 5 7 9 11]}))

    (testing "getting incomplete tasks with an unsuccessful produce task"
      (test-show-tasks
        {:fixtures  [:unsuccessful-product-event]
         :expected  [1 3 5 7 9 11]}))

    (testing "getting incomplete tasks with successful produce task"
      (test-show-tasks
        {:fixtures  [:successful-product-event]
         :expected  [2 3 5 7 9 11]}))

    (testing "getting incomplete tasks with successful produce task"
      (test-show-tasks
        {:fixtures  [:successful-product-event :successful-evaluate-event]
         :expected  [3 5 7 9 11]})))



  (testing "GET /tasks/:id"

    (testing "Getting an unknown task ID"
      (test-app-response
        {:method          :get
         :url             "/tasks/1000"
         :response-tests  [resp/is-client-error-response
                           (resp/has-body "Task not found: 1000")]}))

    (testing "Getting an invalid task ID"
      (test-app-response
        {:method          :get
         :url             "/tasks/unknown"
         :response-tests  [resp/is-client-error-response
                           (resp/has-body "Task not found: unknown")]})))


    (testing "an incomplete produce task"
      (test-get-task
        {:task-id 1
         :files [["short_read_fastq" "s3://reads" "c1f0f"]]}))

    (testing "an successfully completed produce task"
      (test-get-task
        {:task-id 1
         :files [["short_read_fastq" "s3://reads" "c1f0f"]]
         :fixtures [:successful-product-event]
         :events [(mock-event :produce :success)]}))

    (testing "an incomplete evaluate task with no produce files"
      (test-get-task
        {:task-id 2
         :files [["reference_fasta" "s3://ref" "d421a4"]]}))

    (testing "an incomplete evaluate task with a successful produce task"
      (test-get-task
        {:task-id 2
         :files [["reference_fasta" "s3://ref" "d421a4"]
                 ["contig_fasta"    "s3://contigs" "f7455"]]
         :fixtures [:successful-product-event]}))



  (testing "GET /event/:id"

    (testing "a valid unknown event id"
      (test-app-response
        {:method          :get
         :url             "/events/1000"
         :response-tests  [resp/is-client-error-response
                           (resp/has-body "Event not found: 1000")]}))

    (testing "an invalid unknown event id"
      (test-app-response
        {:method          :get
         :url             "/events/unknown"
         :response-tests  [resp/is-client-error-response
                           (resp/has-body "Event not found: unknown")]}))

    (testing "for an unsuccessful product event"
      (test-get-event
        {:event-id 1
         :fixtures [:unsuccessful-product-event]}))

    (testing "for a successful evaluate event"
      (test-get-event
        {:event-id 1
         :fixtures [:successful-evaluate-event]})))



  (testing "POST /events"

    (testing "with a failed produce event"
      (test-app-response
        {:method          :post
         :url             "/events"
         :body            (mock-json-event :produce :failure)
         :content         "application/json"
         :response-tests  [resp/is-ok-response
                           #(resp/has-header % "Location" "/events/1")]
         :db-tests       {"event" 1
                          "event_file_instance" 1}}))

    (testing "with a successful evaluate event"
      (test-app-response
        {:method          :post
         :url             "/events"
         :body            (mock-json-event :evaluate :success)
         :content         "application/json"
         :response-tests  [resp/is-ok-response
                           #(resp/has-header % "Location" "/events/1")]
         :db-tests       {"event" 1
                          "event_file_instance" 1}}))

    (testing "with an unknown file type"
      (test-app-response
        {:method          :post
         :url             "/events"
         :body            (mock-json-event :evaluate :invalid-file)
         :content         "application/json"
         :response-tests  [resp/is-client-error-response
                           (resp/has-body "Unknown file types in request: unknown")]
         :db-tests        {"event" 0
                           "event_file_instance" 0}}))

    (testing "with an unknown metric type"
      (test-app-response
        {:method          :post
         :url             "/events"
         :body            (mock-json-event :evaluate :invalid-metric)
         :content         "application/json"
         :response-tests  [resp/is-client-error-response
                           (resp/has-body "Unknown metrics in request: unknown")]
         :db-tests        {"event" 0
                           "event_file_instance" 0}}))

    (testing "posting the same event twice"
      (let [params {:method   :post
                    :url      "/events"
                    :body     (mock-json-event :produce :failure)
                    :content  "application/json"}]
        (db/empty-database)
        (apply fix/load-fixture fix/base-fixtures)
        (http-request params)
        (test-app-response
          (merge params
                 {:keep-db?        true
                  :response-tests  [resp/is-ok-response
                                    #(resp/has-header % "Location" "/events/2")]
                  :db-tests       {"event" 2
                                   "event_file_instance" 2}})))))


  (testing "GET /benchmarks/:id"

    (testing "an unknown benchmark id"
      (test-app-response
        {:method          :get
         :url             "/benchmarks/unknown"
         :response-tests  [resp/is-client-error-response
                           (resp/has-body "Benchmark not found: unknown")]}))

    (testing "a benchmark with no events"
      (test-app-response
        {:method          :get
         :url             "/benchmarks/453e406dcee4d18174d4ff623f52dcd8"
         :response-tests  [resp/is-ok-response
                           resp/is-not-complete
                           resp/is-not-empty-body
                           bench-test/has-benchmark-fields
                           bench-test/has-task-fields]}))

    (testing "a completed benchmark"
      (test-app-response
        {:method          :get
         :url             "/benchmarks/453e406dcee4d18174d4ff623f52dcd8"
         :fixtures        [:successful_product_event :successful_evaluate_event]
         :response-tests  [resp/is-ok-response
                           resp/is-not-empty-body
                           resp/is-complete
                           bench-test/has-benchmark-fields
                           bench-test/has-task-fields]}))))
