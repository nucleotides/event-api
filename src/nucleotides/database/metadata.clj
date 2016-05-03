(ns nucleotides.database.metadata
  (:require
    [camel-snake-kebab.core           :as ksk]
    [clojure.java.jdbc                :as sql]
    [nucleotides.database.connection  :as con]))


(defn save-metadatum! [table-name entry]
  (let [query "INSERT INTO %1$s (name, description)
               VALUES ('%2$s', '%3$s')
               ON CONFLICT DO NOTHING
               RETURNING id;"]
    (sql/query (con/create-connection)
               (format query
                       (str (ksk/->snake_case_string table-name) "_type")
                       (:name entry)
                       (:desc entry)))))

(defn load-all-metadata [data]
  (dorun
    (for [[table-name entries] data]
      (dorun
        (map (partial save-metadatum! table-name) entries)))))
