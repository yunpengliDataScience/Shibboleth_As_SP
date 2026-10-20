Yes. I would update the guide because we learned an important IIS-specific issue from your installation: **seeing `ShibNative` in IIS Modules does not by itself guarantee that `/Shibboleth.sso/*` is being handled by Shibboleth for a newly created IIS site.**

Add/replace the IIS verification portion of the instructions with the following.

# Updated IIS + Shibboleth Setup and Troubleshooting

Your current environment is:

```
Shibboleth:
C:\Projects\Shibboleth_As_SP\shibboleth-sp\

IIS site:
ShibbolethDemo

IIS URL:
http://localhost:8088

Keycloak:
https://localhost:9991

Realm:
MySecurityRealm

Spring Boot:
http://127.0.0.1:9091
```

The target architecture remains:

```
                       Keycloak
                       SAML IdP
                    localhost:9991
                           ▲
                           │ SAML
                           ▼
Browser ────────► IIS :8088
                      │
                 ShibNative
                      │
                      ▼
                Shibboleth SP
                      │
                 authenticated
                    identity
                      │
                      ▼
                     IIS
                      │
                reverse proxy
                      │
                      ▼
              Spring Boot :9091
                  NO SAML
```

## Phase 1 — Install Shibboleth

Run the Windows Shibboleth SP MSI and use:

```
Installation directory:

C:\Projects\Shibboleth_As_SP\shibboleth-sp\
```

On the installer screen select:

```
☑ Configure IIS support
```

This is important because it installs/registers the Shibboleth IIS native modules.

After installation, reboot Windows.

---

## Phase 2 — Verify the Shibboleth service

Open:

```
services.msc
```

and verify the Shibboleth service is running.

You can also try:

```
sc query shibd_default
```

Then validate the default configuration:

```
cd /d C:\Projects\Shibboleth_As_SP\shibboleth-sp\sbin64

shibd.exe -check
```

Do not continue until the configuration check succeeds.

---

## Phase 3 — Verify the IIS native module

Open:

```
inetmgr
```

Select the **server itself**, then:

```
Modules
```

Your machine has already shown the correct entries:

```
ShibNative

C:\Projects\Shibboleth_As_SP\
shibboleth-sp\lib64\shibboleth\iis7_shib.dll
```

and:

```
ShibNative32

C:\Projects\Shibboleth_As_SP\
shibboleth-sp\lib\shibboleth\iis7_shib.dll
```

Therefore, on your machine:

```
IIS
 │
 ├── ShibNative       ✓
 ├── ShibNative32     ✓
 │
 ▼
Shibboleth installation
```

If these entries are missing, **stop here**. Don't troubleshoot Keycloak, metadata, or Spring yet.

---

# Phase 4 — Create the IIS test site

Create:

```
C:\Projects\Shibboleth_As_SP\iis-test\
```

and:

```
C:\Projects\Shibboleth_As_SP\iis-test\index.html
```

For example:

```
<!DOCTYPE html>
<html>
<head>
    <title>Shibboleth Test</title>
</head>

<body>
    <h1>Shibboleth Test Application</h1>
    <p>Authentication succeeded.</p>
</body>
</html>
```

In IIS Manager:

```
Sites
  ↓
Add Website
```

Configure:

```
Site name:
ShibbolethDemo

Physical path:
C:\Projects\Shibboleth_As_SP\iis-test

Protocol:
http

Port:
8088

Host name:
(empty)
```

Verify:

```
http://localhost:8088/
```

At this stage it should simply display the HTML page.

---

# Phase 5 — IMPORTANT: Find the IIS Site ID

This is the troubleshooting step we discovered during your installation.

In IIS Manager:

```
Sites
  ↓
ShibbolethDemo
  ↓
Advanced Settings...
```

Find:

```
ID
```

For example, it might say:

```
ID = 2
```

**Do not assume that your ID is 2.**

Record the actual number.

For example:

```
ShibbolethDemo

IIS Site ID = 2
IIS Port    = 8088
Host        = localhost
```

We will use this in `shibboleth2.xml`.

---

# Phase 6 — Configure the IIS site in Shibboleth

Open:

```
C:\Projects\Shibboleth_As_SP\
shibboleth-sp\etc\shibboleth\
shibboleth2.xml
```

Find the:

```
<ISAPI>
```

section.

The default installation may contain something similar to:

```
<ISAPI normalizeRequest="true">
    <Site id="1" name="localhost"/>
</ISAPI>
```

If your new `ShibbolethDemo` site has:

```
IIS Site ID = 2
```

add it:

```
<ISAPI normalizeRequest="true">

    <Site id="1"
          name="localhost"/>

    <Site id="2"
          name="localhost"
          scheme="http"
          port="8088"/>

</ISAPI>
```

Again:

```
id="2"
```

is only an example.

Use your **actual IIS Site ID**.

This section essentially tells Shibboleth:

```
IIS Site ID 2
      │
      │ corresponds to
      ▼
http://localhost:8088
```

---

# Phase 7 — Configure the Shibboleth SP identity

In the same `shibboleth2.xml`, find:

```
<ApplicationDefaults>
```

Set/add:

```
<ApplicationDefaults
    entityID="http://localhost:8088/shibboleth"
    REMOTE_USER="uid">
```

Don't delete unrelated existing attributes from `ApplicationDefaults`.

Our SP Entity ID is now:

```
http://localhost:8088/shibboleth
```

---

# Phase 8 — Add Keycloak metadata

Get:

```
https://localhost:9991/realms/MySecurityRealm/protocol/saml/descriptor
```

and save it as:

```
C:\Projects\Shibboleth_As_SP\
shibboleth-sp\etc\shibboleth\
keycloak-metadata.xml
```

Add/configure the metadata provider:

```
<MetadataProvider
    type="XML"
    path="keycloak-metadata.xml"/>
```

Shibboleth now gets Keycloak's:

```
Entity ID
SSO endpoint
SLO endpoint
SAML signing certificate
```

from this file.

---

# Phase 9 — Configure Keycloak as the IdP

Find the `<SSO>` section.

Use:

```
<SSO entityID="https://localhost:9991/realms/MySecurityRealm">
    SAML2
</SSO>
```

As we just discussed, remove:

```
discoveryProtocol="SAMLDS"
```

and:

```
discoveryURL="https://ds.example.org/DS/WAYF"
```

You don't need IdP discovery because you're explicitly using one IdP:

```
Shibboleth
     │
     │ always
     ▼
Keycloak
MySecurityRealm
```

Also verify that:

```
https://localhost:9991/realms/MySecurityRealm
```

exactly matches the `entityID` in `keycloak-metadata.xml`.

---

# Phase 10 — Allow HTTP for this local test

Because we're temporarily using:

```
http://localhost:8088
```

the `<Sessions>` configuration should allow non-SSL handlers for this development test.

For example:

```
<Sessions
    lifetime="28800"
    timeout="3600"
    relayState="ss:mem"
    checkAddress="false"
    handlerSSL="false">
```

The important development setting is:

```
handlerSSL="false"
```

Later, when you move IIS to HTTPS, we should revisit this.

---

# Phase 11 — Configure RequestMapper

Configure the IIS application URL:

```
<RequestMapper type="Native">
    <RequestMap>

        <Host name="localhost"
              scheme="http"
              port="8088"
              authType="shibboleth"
              requireSession="true"/>

    </RequestMap>
</RequestMapper>
```

It's useful to understand the difference between this and `<ISAPI>`:

```
<ISAPI>
   │
   └── Which IIS site is this?


<RequestMapper>
   │
   └── What Shibboleth security policy
       applies to requests for this site?
```

Both matter.

---

# Phase 12 — Validate before restarting

Run:

```
cd /d C:\Projects\Shibboleth_As_SP\shibboleth-sp\sbin64

shibd.exe -check
```

Only proceed if it succeeds.

Then restart Shibboleth:

```
sc stop shibd_default
sc start shibd_default
```

and IIS:

```
iisreset
```

---

# Phase 13 — Test the Shibboleth metadata handler

Now open:

```
http://localhost:8088/Shibboleth.sso/Metadata
```

Correct behavior is:

```
Browser
   │
   │ GET /Shibboleth.sso/Metadata
   ▼
IIS
   │
   │ ShibNative intercepts request
   ▼
Shibboleth
   │
   ▼
SP Metadata XML
```

You should **not** need a physical directory named:

```
Shibboleth.sso
```

and you should **not** create:

```
C:\Projects\Shibboleth_As_SP\
iis-test\Shibboleth.sso\Metadata
```

`Shibboleth.sso` is a virtual Shibboleth handler.

---

# Troubleshooting: `/Shibboleth.sso/Metadata` gives HTTP 404

This is the exact problem you encountered.

Your IIS error showed:

```
HTTP Error 404.0

Module:
IIS Web Core

Handler:
StaticFile

Requested URL:
http://localhost:8088/Shibboleth.sso/Metadata

Physical Path:
C:\Projects\Shibboleth_As_SP\
iis-test\Shibboleth.sso\Metadata
```

The critical clue is:

```
Handler: StaticFile
```

That means IIS is doing:

```
/Shibboleth.sso/Metadata
          │
          ▼
look for physical file

C:\Projects\...\iis-test\
Shibboleth.sso\Metadata
```

instead of:

```
/Shibboleth.sso/Metadata
          │
          ▼
ShibNative
          │
          ▼
Shibboleth handler
```

Use this troubleshooting sequence:

```
1. Does the Shibboleth service run?
           │
           ▼
   shibd.exe -check

2. Does IIS server → Modules contain:
           │
           ├── ShibNative
           └── ShibNative32

3. Does iis7_shib.dll actually exist?
           │
           ▼
   C:\Projects\Shibboleth_As_SP\
   shibboleth-sp\lib64\shibboleth\
   iis7_shib.dll

4. What is ShibbolethDemo's IIS Site ID?
           │
           ▼
   IIS → site → Advanced Settings

5. Does <ISAPI> contain that Site ID?
           │
           ▼
   shibboleth2.xml

6. Does the site's host/scheme/port match?
           │
           ▼
   localhost / http / 8088

7. Run:
           │
           ▼
   shibd.exe -check

8. Restart Shibboleth and IIS.

9. Retry:
           │
           ▼
   /Shibboleth.sso/Metadata
```

On **your machine**, step #2 is already confirmed:

```
ShibNative    ✓

C:\Projects\Shibboleth_As_SP\
shibboleth-sp\lib64\shibboleth\iis7_shib.dll


ShibNative32  ✓

C:\Projects\Shibboleth_As_SP\
shibboleth-sp\lib\shibboleth\iis7_shib.dll
```

So **do not reinstall Shibboleth** based on the 404 you're seeing.

Our next check is the **IIS Site ID and `<ISAPI>` mapping**.

---

# Phase 14 — Only after Metadata works: configure Keycloak

Do **not** troubleshoot Keycloak until this works:

```
http://localhost:8088/Shibboleth.sso/Metadata
```

Once it produces XML, save that metadata and import it into Keycloak:

```
MySecurityRealm
   ↓
Clients
   ↓
Import client
```

This creates a SAML client representing:

```
Shibboleth SP
```

The expected SP Entity ID is:

```
http://localhost:8088/shibboleth
```

and the important ACS endpoint should be similar to:

```
http://localhost:8088/Shibboleth.sso/SAML2/POST
```

---

# Phase 15 — Configure `uid`

In Keycloak, configure a SAML mapper so:

```
Keycloak username
        │
        ▼
SAML attribute

uid
```

For user `john`:

```
username = john

       ↓

SAML

uid = john
```

Then in:

```
C:\Projects\Shibboleth_As_SP\
shibboleth-sp\etc\shibboleth\
attribute-map.xml
```

map:

```
<Attribute name="uid" id="uid"/>
```

Together with:

```
REMOTE_USER="uid"
```

the complete mapping becomes:

```
Keycloak
username = john
      │
      ▼
SAML
uid = john
      │
      ▼
Shibboleth
uid = john
      │
      ▼
REMOTE_USER
john
```

---

# Phase 16 — Test the Shibboleth session

After Keycloak authentication works, open:

```
http://localhost:8088/Shibboleth.sso/Session
```

Check that the session is active and that `uid` is present.

At this checkpoint:

```
Keycloak
    ↕
   SAML
    ↕
Shibboleth
    ↕
   IIS
```

must work completely.

---

# Phase 17 — Spring Boot comes LAST

Only then introduce:

```
Spring Boot
http://127.0.0.1:9091
```

Spring should have:

```
server:
  address: 127.0.0.1
  port: 9091
```

and **no Spring SAML configuration**.

The final architecture becomes:

```
                    Keycloak
                       ▲
                       │
                      SAML
                       │
                       ▼
Browser ────────► IIS :8088
                      │
                 ShibNative
                      │
                 Shibboleth
                      │
                 uid = john
                      │
                      ▼
                     IIS
                      │
                reverse proxy
                      │
                      ▼
             Spring Boot :9091
                    NO SAML
```

For where you are **right now**, don't move to Keycloak or Spring yet. Your immediate checkpoint is to find the actual **IIS Site ID for `ShibbolethDemo`**, verify that ID appears correctly in `<ISAPI>`, run `shibd.exe -check`, restart the services, and retry `/Shibboleth.sso/Metadata`.
