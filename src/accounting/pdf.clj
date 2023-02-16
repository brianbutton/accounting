(ns accounting.pdf
  (:require
    [clojure.string :as str]
    [pdfboxing.text :as text]))

(defn format-row [row]
  (let [[date&type amount] (remove #(= "" %) (str/split row #"\s\s"))]
    {:date (subs date&type 0 10)
     :charge-type (subs date&type 22)
     :amount (str "-" (str/replace (str/trim amount) #"[,]" "."))}))

(defn calculate-rates [file-path]
  (->>
    (text/extract file-path)
    (str/split-lines)
    (filter #(re-matches #"\d*\.\d*\.\d*\s\d*\.\d*\.\d*.*" %))
    (map format-row)))