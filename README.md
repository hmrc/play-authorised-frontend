#play-authorised-frontend

[![Build Status](https://travis-ci.org/hmrc/play-authorised-frontend.svg)](https://travis-ci.org/hmrc/play-authorised-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/play-authorised-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/play-authorised-frontend/_latestVersion) [![Apache-2.0 license](http://img.shields.io/badge/license-Apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

This component can be used in Frontends to allow Authorised Delegation of users.

## Installing
 
Include the following dependency in your SBT build
 
``` scala
resolvers += Resolver.bintrayRepo("hmrc", "releases")
 
libraryDependencies += "uk.gov.hmrc" %% "play-authorised-frontend" % "[INSERT-VERSION]"
```

###Actions

It provides two traits from which user-aware controllers can be extended:

* Actions - This provides methods: `AuthorisedFor` and `AuthenticatedBy` which allow different levels of authorisation, and provides the AuthContext object to your controller.
* DelegationAwareActions - This is used identically to Actions, but works in situations where the code allows _delegated_ access.  These actions require an `AuthConnector` plus a `DelegationConnector` with associated configuration.

###Connectors

The following connectors should be provided:

* The standard `AuthorisedFor` and `AuthenticatedBy` Actions requires a single `AuthConnector` to be provided.
* When using `DelegationAwareActions`, both an `AuthConnector` and `DelegationConnector` should be provided.

It is vital to provide companion objects and all necessary configuration for the `AuthConnector` and `DelegationConnector`.

###User and AuthContext

The `AuthContext` class is the replacement of the `User` class. The `User` is a familiar remnant of previous incarnations of play-frontend and play-authorised-frontend.

`AuthContext` provides three fields:

* *user* - This represents the _logged in user_; the person at the keyboard currently using the application
* *principal* - This is the entity whose tax accounts are being used (person, organisation or holder of tax accounts).
* *attorney* - This is an optional entity acting _on behalf of_ the principal.


    case class User(userId: String,
                    userAuthority: Authority,
                    nameFromGovernmentGateway: Option[String]
                    decryptedToken: Option[String]
                    actingAsAttorneyFor:Option[ActingAsAttorneyFor])

To help migrating, the mapping from `User` to `AuthContext` fields are as follows:

        
`User.userId` == `AuthContext.user.userId`

`User.userAuthority.uri` == `AuthContext.user.userId`

`User.userAuthority.accounts` == `AuthContext.principal.accounts`

`User.userAuthority.loggedInAt` == `AuthContext.user.loggedInAt`

`User.userAuthority.previouslyLoggedInAt` == `AuthContext.user.previouslyLoggedInAt`

`User.nameFromGovernmentGateway` == either `AuthContext.principal.name` or `AuthContext.attorney.name`

`User.decryptedToken` == `AuthContext.user.governmentGatewayToken`

`User.actingAsAttorneyFor` - this is deprecated and has been removed in favour of the principal/attorney fields.


###Service Origin for Metrics for Securing Our Services

The `GovernmentGateway` trait allows to set a `origin` property which will be used by analytics tools to
track the entire login journey.

By default this is the name of the application (`appName` from `application.conf`). You can set a value
that is different from the application name globally in `application.conf` with the `sosOrigin` property.

If you need to dynamically set the value per request, you need to override the `origin` property in
the `GovernmentGateway` trait. 


###Controller

If you have a service that initiates the delegation flow, where a logged in user starts acting on behalf of another user, then you need to provide a controller extending `uk.gov.hmrc.play.frontend.auth.Delegator`.

This provides two methods:

* `startDelegationAndRedirect()`: This requires a `DelegationContext` object, containing the information you want to be made available in the target frontend.  That is, the name of the principal, the name of the attorney, the account details which are to be made available in the target, and the link allowing the attorney to return to their home page (e.g. the agent dashboard). This method makes a service call to the Delegation microservice which stores the provided `DelegationContext`, it then adds a delegation flag to the session and redirects to the target frontend address (i.e. that of the attorney's client).
* `endDelegation()`: when the attorney wants to return to their origin, they click the callback link provided in the original `startDelegationAndRedirect()` call.
This should hit an endpoint which calls `endDelegation()` on the `Delegator`.  The `endDelegation()` method removes the current delegation context from the Delegation 
microservice, and removes the delegation flag from the session.


## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
