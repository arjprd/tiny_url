# Analytics

## Time based Key (t_key)

* format for t_key has to be picked form config
* the config can be combintation of `year`, `month`, `day`, `hour`, `minute`, `seconds` in the same order.
* for example `year.month` is possible but `year.day` or  `month.year` is not. `year.month.day` is a valid one.
* the markers have to be replaced with curresponding value based on the provided timestamp

## Event Capture
* create service to capture analytics events async 
* service would have method by name click with 2 params `shortUrl` from `/{shortUrl}` and timestamp
* the method will be called from `UrlService.getLongUrl` just before providing a successful response.
* the method updates a hashset with key as `analytics:t_key` of timestamp, field as `shortUrl` and value is incremented each time the method is called.

## Event Dump

* a scheduled job that runs at configured time would look for key `analytics:t_key` of current timstamp - 1 hour.
* resolve the url_id if has _ then base62 decode else look in custom_code_url table to get url_id
* timestamp of t_key with rest as 0. example if the config was `year.month.day.hour` for the current timestamp - 1 hour ie `2025-12-21T10:01:00Z` then  `2025-12-21T10:00:00Z` is set to db while dumpting
* dump the record to table `short_url_click_analytics`

# API

## Get Analytics

* **Method** : GET
* **PATH** : /url/{shortUrlCode}
* **Authorization**: Bearer <token>
* **Query Param**: start_date: timestamp, end_date: timestamp
* **Response Body** : 
    ```
    [
        {
            "time": timestamp,
            "count": <long>
        }
    ]
    ```

* get url_id from the `shortUrlCode`
* check if current user is owner for the url_id
* get all count from short_url_click_analytics for the time range provided

# DB Schema 

## short_url_click_analytics
primary key is composite key of time and url_id
| column_name    | type | Indexing |
| -------- | ------- | ----------- |
| time  | timestamp   | indexed for range query |
| url_id | big int | forign key short_url.id indexed |
| count | bigint | |