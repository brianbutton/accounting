(ns accounting.google-sheets
  (:require [clojure.java.io :as io])
  (:import
   (com.google.api.client.extensions.java6.auth.oauth2 AuthorizationCodeInstalledApp)
   (com.google.api.client.googleapis.auth.oauth2 GoogleClientSecrets GoogleAuthorizationCodeFlow$Builder)
   (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
   (com.google.api.client.json.jackson2 JacksonFactory)
   (com.google.api.client.extensions.jetty.auth.oauth2 LocalServerReceiver$Builder)
   (com.google.api.client.util.store DataStoreFactory FileDataStoreFactory)
   (com.google.api.services.sheets.v4 Sheets Sheets$Builder)
   (com.google.api.services.sheets.v4.model AppendCellsRequest BatchUpdateSpreadsheetRequest CellData ExtendedValue GridCoordinate Request RowData UpdateCellsRequest ValueRange)
   (java.util ArrayList)))
;https://developers.google.com/resources/api-libraries/documentation/sheets/v4/csharp/latest/index.html

(defn google-service
  [{:keys [^String application-name ^DataStoreFactory tokens-directory
           credentials-filename authorize scopes access-type port]}]
  (let [credentials
        (->
          credentials-filename
          io/resource
          io/reader)

        tokens
        (->
          tokens-directory
          io/file
          FileDataStoreFactory.)

        json-factory (JacksonFactory/getDefaultInstance)
        http-transport (GoogleNetHttpTransport/newTrustedTransport)
        client-secrets (GoogleClientSecrets/load json-factory credentials)

        flow
        (->
          (GoogleAuthorizationCodeFlow$Builder.
            http-transport
            json-factory
            client-secrets
            scopes)
          (.setDataStoreFactory tokens)
          (.setAccessType access-type)
          .build)

        receiver
        (->
          (LocalServerReceiver$Builder.)
          (.setPort port)
          .build)]
    (->
      (Sheets$Builder.
        http-transport json-factory
        (->
          (AuthorizationCodeInstalledApp. flow receiver)
          (.authorize authorize)))
      (.setApplicationName application-name)
      .build)))

(defn set-value-by-type [value]
  (->
    (CellData.)
    (.setUserEnteredValue
      (cond
        (or (double? value) (integer? value))
        (->
          (ExtendedValue.)
          (.setNumberValue (double value)))

        (boolean? value)
        (->
          (ExtendedValue.)
          (.setBoolValue value))

        (= \= (first value))
        (->
          (ExtendedValue.)
          (.setFormulaValue value))

        :else
        (->
          (ExtendedValue.)
          (.setStringValue (str value)))))))

(defn row-data [row]
  (->
    (RowData.)
    (.setValues
      (map set-value-by-type row))))

(defn set-requests [sheet-id batch]
  (->
    (BatchUpdateSpreadsheetRequest.)
    (.setRequests
      [(->
         (Request.)
         (.setAppendCells
           (->
             (AppendCellsRequest.)
             (.setSheetId sheet-id)
             (.setRows (map row-data batch))
             (.setFields "userEnteredValue,userEnteredFormat"))))])))

(defn get-values [^Sheets service id range]
  (let [^ValueRange response
        (->
          service
          .spreadsheets
          .values
          (.get id range)
          .execute)]
    (.getValues response)))

(defn write-values [^Sheets service id sheet-id values row-index]
  (let [write-request
        (->
          (Request.)
          (.setUpdateCells
            (->
              (UpdateCellsRequest.)
              (.setStart
                (->
                  (GridCoordinate.)
                  (.setSheetId (int sheet-id))
                  (.setRowIndex (int row-index))
                  (.setColumnIndex (int 0))))
              (.setRows [(row-data values)])
              (.setFields "userEnteredValue,userEnteredFormat"))))]
    (->
      service
      (.spreadsheets)
      (.batchUpdate
        id
        (->
          (BatchUpdateSpreadsheetRequest.)
          (.setRequests [write-request])))
      .execute)))

(defn append-values [^Sheets service id sheet-id rows]
  (let [rows (ArrayList. rows)
        sheet-id (int sheet-id)
        num-cols (int (count (first rows)))
        part-size (long (/ 10000 num-cols))
        batches (partition part-size part-size [] rows)
        first-batch (set-requests sheet-id (first batches))]
    (doall
      (cons
        (->
          service
          (.spreadsheets)
          (.batchUpdate id
            first-batch)
          (.execute))
        (map
          (fn [batch]
            (-> service
              (.spreadsheets)
              (.batchUpdate
                id
                (set-requests sheet-id batch))
              (.execute)))
          (rest batches))))))