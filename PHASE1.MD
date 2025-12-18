# DB Schema:
## short_url
| column_name    | type | Indexing |
| -------- | ------- | ----------- |
| id  | Big Int (auto)   | primary key |
| long_url | text     | |
| long_url_hash    | char(64)    | index for faster query |
| created_at    | timestampz    | |

# APIs

## 1. Shorten API

* **Method** : POST
* **PATH** : /shorten
* **RequestBody** : ``{ url: <string> }``
* **ResponseBody** : ``{ short_url: <string> }``

### Logic
1. Validate if ``body.url`` holds a valid URL expression
2. Create ``long_url_hash`` from ``body.url`` using ``SHA256`` algorithm
3. Insert the record into table if ``long_url_hash`` and ``long_url`` not exists
4. Create a Base62 encoding of the number
5. Return ``response.body.short_url={host}/{encoded_string}``

## 2. /{shortURL} API

* **Method** : GET
* **PATH** : /{shortURL}
* **Response** : 301 redirect url

### Logic
1. Base62 decode of ``path.shortURL``
2. Get record from table
3. Create a Base62 encoding of the number
4. Return ``response.body.short_url={host}/{encoded_string}``

## Error Responses

### Invalid Url

**HTTP STATUS**: 
```json
{
    "code": "INVALID_URL",
    "message": "Provided URL is invalid"
}

### Already Present

**HTTP STATUS**: 409
```json
{
    "code": "DUPLICATE_REQUEST",
    "message": "A short URL exists for the long URL"
}
```

### Not Found

**HTTP STATUS**: 404
```json
{
    "code": "NO_RECORD",
    "message": "A long URL does exists for the short URL"
}
```

### Unhandled

**HTTP STATUS**: 500
```json
{
    "code": "SERVER_ERROR",
    "message": "Something went wrong"
}
``