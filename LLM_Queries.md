# PHASE 1 Query

```
@PHASE1.MD 
* crearte entity for db schema
* create utitlity for base62 with encode and decode methods
* implement the APIs
* have request response models maintained seperately
* ensure to use only the provided error responses.
```
```
use lombok
```

# Phase 2

```
@PHASE2.md 
* crearte entity for db schema
* implement or update the APIs
* have request response models maintained seperately
* ensure to use only the provided error responses in the doc and the once already implemented in the app.
```

```
@PHASE2.md 
* implement rate limitng
* check has to be made as a prefilter for api calls
```

```
create an openapi doc endpoint
```

# Phase 3

```
@PHASE3.md implement caching for `/{shortURL}` API
```

```
@PHASE3.md implement login api
```

```
@PHASE3.md modify authentication mechanism from basic to bearer. Follow the token verification logic
```

```
@src/main/java/com/example/tinyurl/repository/UserRepository.java write following test cases
1. create a user record and save, then make query `existsByUsername` for the user name and check if true. make a call again with different username to check if false.
2. 2 records with same username should not be possible
3. create a user and save and check if createAt and updatedAt has a valid value
```

```
@src/main/java/com/example/tinyurl/repository/ShortUrlRepository.java create test case for following scenarios
1. create a @src/main/java/com/example/tinyurl/entity/User.java and save then create a @src/main/java/com/example/tinyurl/entity/ShortUrl.java record and save. Verify created at and updatedat has valid values
2. create a @src/main/java/com/example/tinyurl/entity/ShortUrl.java with invalid user id should get exception
3. create multiple @src/main/java/com/example/tinyurl/entity/ShortUrl.java records with valid user and has long_url hash same but different valies. check if no exception happens and both values are present in db on get
```

```
@src/main/java/com/example/tinyurl/service/RateLimitService.java write test case for following scenarios:
1. invoke repeatedly with a gam of 3 seconds `checkGetRateLimit` with a shortUrl and should return true for the first 10 invcation then it shoul return false. again from 21 st invocation it shold return true.
2.  invoke repeatedly with a gam of 5 seconds `checkPostRateLimit` with a shortUrl and should return true for the first 5 invcation then it shoul return false. again from 11 th invocation it shold return true.
```

```
@src/main/java/com/example/tinyurl/service/TokenAuthenticationService.java create following test case :
1. create hash set in redis with key 'token:123'  field 'abcdef' and value true with ttl of 60 sec
2. encrypt 123
3. invoke `verifyToken` with string `encrypt(123).'abcdef'`
4. cast the return of the function to @src/main/java/com/example/tinyurl/util/CustomAuthentication.java  
5. assert and check getName, getCredentials, getUserId
```
```
@src/main/java/com/example/tinyurl/service/TokenAuthenticationService.java create following test case :
1. encrypt 123
2. invoke `verifyToken` with string `encrypt(123).'abcdef'`
4. return should be empty
```
```
@src/main/java/com/example/tinyurl/service/TokenAuthenticationService.java create following test case :
1. create hash set in redis with key 'token:123'  field 'abcdef' and value true with ttl of 60 sec
2. encrypt 123
3. invoke `verifyToken` with string `encrypt(123).xasdert`
4. the response of the function should be empty
```

```
@src/main/java/com/example/tinyurl/service/UrlService.java create test cases for following scenarios:
1. invoke `shortenUrl` with and invalid url should assert getResponese() should be null, 
getStatus should be 400,  getError().getCode() should be INVALID_URL and getError().getMessage() should be "Provided URL is invalid"

2. create a user and save, now invoke `shortenUrl` with a url twice. First time it should respond with getResponese() should be a nonempty string, 
getStatus should be 200 and getError() shold be null. For the second time getResponese() should be null, 
getStatus should be 409,  getError().getCode() should be DUPLICATE_REQUESTand getError().getMessage() should be "A short URL exists for the long URL"
```

```
@src/main/java/com/example/tinyurl/service/UrlService.java create test case for followign scenarios
1. create a user and save, create a shortenUrl using `shortenUrl` method.  Invoke `getLongUrl` with the short url from `shortUrl` method call. Retirn of getLongUrl() should get the actual long url.
2. create a user and save, create a shortenUrl using `shortenUrl` method.  Invoke `getLongUrl` concurrently 10 times with the short url from `shortUrl` method call. Only one call should be made to db.
3. Invoke `getLongUrl`  with the a random url. getLongUrl() should be null, getError().getCode() is NO_RECORD, getError().getMessage() is "A long URL does exists for the short URL" and getStatus() should be 404
```

```
@src/main/java/com/example/tinyurl/service/UserService.java create test case for following scenarios
1. `createUser` with non alpha numeric username
2. `createUser` twise for same username, first one would be successfull second one would be 409.
3. `login` with non alpha numberic username
4. `login` with wrong password
5. `login` with wrong username
6. `login` with correct username and password. verify the token by invoking @src/main/java/com/example/tinyurl/service/TokenAuthenticationService.java `verifyToken`
```

```
@PHASE4.md implement expiration for short_url and custom url
```

```
@src/test/java/com/example/tinyurl/service/UrlServiceTest.java add following scenario
1. `shortenUrl` with customShortUrl and response should have the customShortUrl in shortUrl
2. `expiry` passed as current timestamp + 60 seconds in `shortenUrl`. Verify `getLongUrl` then wait for 65 seconds then perform `getLongUrl` again should see not found error.
```

```
@src/main/java/com/example/tinyurl/controller/UrlController.java create one test case each for different responses. consider success case for customShortUrl, expiry and without. for expiry verify by checking in db record
```

```
@src/main/java/com/example/tinyurl/controller/UserController.java  create one test case each for different responses.  the apis give
```

```
@PHASE5.md implement db schema for short_url_click_analytics
```

```
@PHASE5.md implement service to capture analytics info to redis
```