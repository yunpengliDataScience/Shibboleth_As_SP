Absolutely. Since your Shibboleth SP is now installed at:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\
```

we should use that path consistently. Below is a regenerated end-to-end tutorial for your exact architecture and existing Keycloak/Spring Boot setup.

# Shibboleth SP + IIS + Keycloak IdP + Spring Boot

## 1. What we are building

This is different from your earlier Spring Security SAML application.

Previously:

```text
Browser
   ↓
Spring Boot / Spring Security
   ↕ SAML
Keycloak
```

Spring Boot itself was the SAML SP.

Now we want:

```text
                         ┌─────────────────────┐
                         │      Keycloak       │
                         │      SAML IdP       │
                         │ https://localhost:  │
                         │       9991          │
                         └─────────▲───────────┘
                                   │
                                  SAML
                                   │
                                   ▼
Browser ───────────────► IIS + Shibboleth SP
                              │
                              │ authenticated
                              │ request
                              ▼
                         Spring Boot
                      http://localhost:9091

                         NO SAML CODE
```

Responsibilities are therefore:

| Component   | Responsibility                                                       |
| ----------- | -------------------------------------------------------------------- |
| Keycloak    | SAML Identity Provider                                               |
| Shibboleth  | SAML Service Provider                                                |
| IIS         | Hosts/protects the public application and integrates with Shibboleth |
| Spring Boot | Regular application; doesn't process SAML                            |

The important relationship is:

```text
Shibboleth SP ←────── SAML ──────→ Keycloak IdP
```

**not**

```text
Spring Boot ←──── SAML ────→ Keycloak
```

---

# PART 1 — Your environment

We'll use these values throughout the tutorial.

### Keycloak

```text
URL:
https://localhost:9991

Realm:
MySecurityRealm

IdP Entity ID:
https://localhost:9991/realms/MySecurityRealm

IdP Metadata:
https://localhost:9991/realms/MySecurityRealm/protocol/saml/descriptor
```

### Shibboleth

Installation:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\
```

We'll initially expose the test application through IIS at:

```text
http://localhost:8088
```

SP Entity ID:

```text
http://localhost:8088/shibboleth
```

### Spring Boot

```text
http://localhost:9091
```

Spring will eventually be accessed through IIS instead of directly by the browser.

---

# PART 2 — Install Shibboleth

## 2. Run the Shibboleth MSI

You have already selected:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\
```

as the installation directory.

On the screen you showed me, select:

```text
☑ Configure IIS support
```

Then continue the installation.

The installer will install approximately:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\
│
├── etc\
│   └── shibboleth\
│       ├── shibboleth2.xml
│       ├── attribute-map.xml
│       ├── attribute-policy.xml
│       └── ...
│
├── sbin64\
│       └── shibd.exe
│
├── lib64\
│       └── ...
│
├── var\
│   └── log\
│
└── ...
```

The installer also installs the Shibboleth daemon as a Windows service and configures the IIS integration.

After installation, reboot Windows before troubleshooting IIS/Shibboleth integration.

---

# PART 3 — Verify Shibboleth before changing anything

This is important. Don't start changing Keycloak or XML until we know the default installation works.

## 3. Check the Windows service

Open:

```text
Win + R
```

and enter:

```text
services.msc
```

Look for the Shibboleth service.

You can also use:

```bat
sc query shibd_default
```

If that isn't the installed service name, use `services.msc` to see the exact name.

It should be:

```text
RUNNING
```

---

# PART 4 — Check the Shibboleth configuration

Open an Administrator Command Prompt:

```bat
cd /d C:\Projects\Shibboleth_As_SP\shibboleth-sp\sbin64
```

Run:

```bat
shibd.exe -check
```

We want the configuration validation to succeed.

Get into the habit of doing this after editing:

```text
shibboleth2.xml
attribute-map.xml
```

before restarting Shibboleth.

---

# PART 5 — Locate the configuration

Your main Shibboleth configuration directory is now:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\etc\shibboleth\
```

The most important files for this project will be:

```text
shibboleth2.xml
attribute-map.xml
keycloak-metadata.xml     ← we'll add this
```

So whenever I previously referred to:

```text
C:\opt\shibboleth-sp\etc\shibboleth\
```

ignore that.

Your actual path is:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\etc\shibboleth\
```

---

# PART 6 — Verify IIS integration

Open:

```text
Win + R
```

then:

```text
inetmgr
```

IIS Manager should open.

Select the server in the left panel and open:

```text
Modules
```

Look for the Shibboleth native module installed by the MSI.

Conceptually, you now have:

```text
IIS
 │
 │ Shibboleth IIS native module
 │
 ▼
Shibboleth SP
 │
 ▼
shibd Windows service
```

At this point Keycloak isn't involved yet.

---

# PART 7 — Create a simple IIS test application

Before involving Spring Boot, I recommend proving that:

```text
IIS
 ↓
Shibboleth
 ↓
Keycloak
```

works independently.

Create:

```text
C:\Projects\Shibboleth_As_SP\iis-test\
```

Create:

```text
C:\Projects\Shibboleth_As_SP\iis-test\index.html
```

containing:

```html
<!DOCTYPE html>
<html>
<head>
    <title>Shibboleth Test</title>
</head>
<body>

<h1>Shibboleth Test Application</h1>

<p>If you see this page after Keycloak login,
Shibboleth authentication worked.</p>

</body>
</html>
```

---

# PART 8 — Create an IIS site

In IIS Manager:

```text
Sites
 ↓
Add Website
```

Configure:

```text
Site name:
ShibbolethDemo

Physical path:
C:\Projects\Shibboleth_As_SP\iis-test

Type:
http

IP address:
All Unassigned

Port:
8088

Host name:
(empty)
```

Start the site.

Now visit:

```text
http://localhost:8088/
```

You should see:

> Shibboleth Test Application

There should **not** be a Keycloak login yet.

This verifies:

```text
Browser
   ↓
IIS :8088
   ↓
index.html
```

---

# PART 9 — Back up Shibboleth configuration

Before editing:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\etc\shibboleth\shibboleth2.xml
```

make a copy:

```text
shibboleth2.xml.original
```

Do the same for:

```text
attribute-map.xml
```

For example:

```text
attribute-map.xml.original
```

---

# PART 10 — Configure Shibboleth's SP Entity ID

Open:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\etc\shibboleth\shibboleth2.xml
```

Find:

```xml
<ApplicationDefaults
```

We'll use:

```xml
<ApplicationDefaults
    entityID="http://localhost:8088/shibboleth"
    REMOTE_USER="uid">
```

There may already be additional attributes on that element. **Don't delete them merely to make the XML look like this example.**

The important settings are:

```text
entityID =
http://localhost:8088/shibboleth

REMOTE_USER =
uid
```

This establishes:

```text
Shibboleth SP identity
        =
http://localhost:8088/shibboleth
```

---

# PART 11 — Download Keycloak metadata

Your Keycloak IdP metadata is:

```text
https://localhost:9991/realms/MySecurityRealm/protocol/saml/descriptor
```

Open that URL in your browser.

Save the XML as:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\etc\shibboleth\keycloak-metadata.xml
```

Your directory should now contain:

```text
etc\shibboleth\
│
├── shibboleth2.xml
├── attribute-map.xml
├── attribute-policy.xml
├── keycloak-metadata.xml
└── ...
```

---

# PART 12 — Configure Keycloak metadata in Shibboleth

Inside `shibboleth2.xml`, find the existing `MetadataProvider` examples/configuration.

Add an XML metadata provider for your local Keycloak metadata:

```xml
<MetadataProvider
    type="XML"
    path="keycloak-metadata.xml"/>
```

This tells Shibboleth:

```text
keycloak-metadata.xml
        │
        ├── Keycloak Entity ID
        ├── SAML SSO endpoint
        ├── SAML logout endpoint
        └── Keycloak public signing certificate
```

That last item is particularly important.

When Keycloak sends:

```text
SAMLResponse
       +
digital signature
```

Shibboleth can verify the signature using the public certificate in the Keycloak metadata.

---

# PART 13 — Configure Keycloak as Shibboleth's IdP

Inside the `<Sessions>` section of `shibboleth2.xml`, configure the SSO provider.

Conceptually:

```xml
<SSO entityID="https://localhost:9991/realms/MySecurityRealm">
    SAML2
</SSO>
```

But there's one important rule:

**Open `keycloak-metadata.xml` and look at the top-level `EntityDescriptor entityID`. Use that exact value.**

For example, if it says:

```xml
<EntityDescriptor
    entityID="https://localhost:9991/realms/MySecurityRealm">
```

then use:

```xml
<SSO entityID="https://localhost:9991/realms/MySecurityRealm">
    SAML2
</SSO>
```

Don't put the SSO endpoint itself into `entityID`.

For example, this:

```text
https://localhost:9991/realms/MySecurityRealm/protocol/saml
```

is an **endpoint**.

Whereas:

```text
https://localhost:9991/realms/MySecurityRealm
```

is the **IdP Entity ID**, assuming that's what your metadata declares.

---

# PART 14 — Development HTTP configuration

We're temporarily using:

```text
http://localhost:8088
```

rather than HTTPS.

Find `<Sessions ...>`.

For this development environment, it may resemble:

```xml
<Sessions
    lifetime="28800"
    timeout="3600"
    relayState="ss:mem"
    checkAddress="false"
    handlerSSL="false">
```

The significant development setting is:

```xml
handlerSSL="false"
```

because we're allowing Shibboleth handler endpoints such as:

```text
http://localhost:8088/Shibboleth.sso/...
```

For production, use HTTPS and don't design around this HTTP exception.

---

# PART 15 — Configure protection for localhost:8088

In `shibboleth2.xml`, locate the `RequestMapper`.

For our local test site, conceptually:

```xml
<RequestMapper type="Native">
    <RequestMap>
        <Host
            name="localhost"
            scheme="http"
            port="8088"
            authType="shibboleth"
            requireSession="true"/>
    </RequestMap>
</RequestMapper>
```

This means:

```text
Browser requests
http://localhost:8088/
          │
          ▼
Does Shibboleth session exist?
          │
     ┌────┴────┐
     │         │
    YES        NO
     │         │
     │         ▼
     │      Keycloak
     │
     ▼
IIS application
```

---

# PART 16 — Validate configuration

Now run:

```bat
cd /d C:\Projects\Shibboleth_As_SP\shibboleth-sp\sbin64
shibd.exe -check
```

Don't continue if this reports an XML/configuration error.

Fix that first.

Then restart the Shibboleth service. Depending on the service name installed on your system:

```bat
sc stop shibd_default
sc start shibd_default
```

Then restart IIS:

```bat
iisreset
```

These commands generally require an elevated Command Prompt.

---

# PART 17 — Test Shibboleth SP metadata

Now try:

```text
http://localhost:8088/Shibboleth.sso/Metadata
```

This is a very important checkpoint.

You should get XML containing something conceptually like:

```xml
<EntityDescriptor
    entityID="http://localhost:8088/shibboleth">

    <SPSSODescriptor ...>

        ...

        <AssertionConsumerService
            Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
            Location="http://localhost:8088/Shibboleth.sso/SAML2/POST"
            ... />

    </SPSSODescriptor>

</EntityDescriptor>
```

The important values are:

```text
SP Entity ID:

http://localhost:8088/shibboleth
```

and:

```text
ACS:

http://localhost:8088/Shibboleth.sso/SAML2/POST
```

Compare this to your previous Spring Security project:

```text
OLD — Spring Security SP

SP:
Spring Boot

Entity ID:
SpringBoot_SAML_App

ACS:
/login/saml2/sso/keycloak

Metadata:
/saml2/metadata/keycloak
```

Now:

```text
NEW — Shibboleth SP

SP:
Shibboleth

Entity ID:
http://localhost:8088/shibboleth

ACS:
/Shibboleth.sso/SAML2/POST

Metadata:
/Shibboleth.sso/Metadata
```

That's the architectural transition we're trying to demonstrate.

---

# PART 18 — Create a NEW Keycloak client

Keep your existing client:

```text
SpringBoot_SAML_App
```

because that's useful for your original Spring Security SAML project.

Create a second client for Shibboleth.

In Keycloak:

```text
MySecurityRealm
    ↓
Clients
    ↓
Create client
```

Choose:

```text
Client type:
SAML
```

Use:

```text
Client ID:

http://localhost:8088/shibboleth
```

You could give it a descriptive name such as:

```text
Shibboleth SP Demo
```

So your realm now conceptually contains:

```text
MySecurityRealm
│
├── SpringBoot_SAML_App
│       │
│       └── SP = Spring Security
│
└── http://localhost:8088/shibboleth
        │
        └── SP = Shibboleth
```

This is actually a useful way to compare the two SAML architectures.

---

# PART 19 — Prefer importing Shibboleth metadata into Keycloak

Instead of manually entering every Shibboleth URL, save:

```text
http://localhost:8088/Shibboleth.sso/Metadata
```

as:

```text
shibboleth-sp-metadata.xml
```

Then use Keycloak's SAML client import capability.

That lets Keycloak learn things such as:

```text
SP Entity ID
ACS endpoints
bindings
SP signing certificate
```

from Shibboleth's metadata.

After importing, verify rather than blindly assuming every generated setting is what you want.

---

# PART 20 — Important Keycloak settings

The client represents:

```text
Shibboleth SP
```

not Spring Boot.

Therefore the important values should correspond to:

```text
Client ID:
http://localhost:8088/shibboleth
```

and the SAML POST destination/ACS:

```text
http://localhost:8088/Shibboleth.sso/SAML2/POST
```

The exact Keycloak UI labels can vary by version, but the underlying relationship should be:

```text
Keycloak
   │
   │ SAMLResponse
   ▼
Shibboleth ACS

/Shibboleth.sso/SAML2/POST
```

Spring's old endpoint:

```text
/login/saml2/sso/keycloak
```

should play **no role whatsoever** in this new application.

---

# PART 21 — Configure the username mapper in Keycloak

Let's continue using the same idea you learned with your previous project:

```text
uid = username
```

In the Shibboleth SAML client in Keycloak, add a protocol mapper that sends the Keycloak username as:

```text
uid
```

If the user is:

```text
username:
john
```

we want Keycloak's SAML assertion to contain:

```xml
<saml:Attribute Name="uid">
    <saml:AttributeValue>john</saml:AttributeValue>
</saml:Attribute>
```

Conceptually:

```text
Keycloak

username = john
       │
       │ mapper
       ▼
SAML

uid = john
       │
       ▼
Shibboleth
```

---

# PART 22 — Configure `attribute-map.xml`

Now Shibboleth has to recognize the SAML attribute.

Open:

```text
C:\Projects\Shibboleth_As_SP\shibboleth-sp\etc\shibboleth\attribute-map.xml
```

Configure the mapping for the exact SAML attribute name you're sending.

Conceptually:

```xml
<Attribute name="uid" id="uid"/>
```

Now:

```text
SAML attribute
uid
 │
 ▼
Shibboleth attribute
uid
```

And because `shibboleth2.xml` contains:

```xml
REMOTE_USER="uid"
```

we get:

```text
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

This is the Shibboleth equivalent of what you previously did with Spring:

```java
principal.getFirstAttribute("uid")
```

except Spring isn't parsing SAML anymore.

---

# PART 23 — Validate everything again

Run:

```bat
cd /d C:\Projects\Shibboleth_As_SP\shibboleth-sp\sbin64
shibd.exe -check
```

Then restart Shibboleth and IIS.

---

# PART 24 — Test login

Open an Edge InPrivate window:

```text
Ctrl + Shift + N
```

Visit:

```text
http://localhost:8088/
```

Expected sequence:

```text
Browser
   │
   │ GET /
   ▼
IIS :8088
   │
   ▼
Shibboleth
   │
   │ no Shibboleth session
   ▼
Create SAML AuthnRequest
   │
   ▼
Browser
   │
   ▼
Keycloak :9991
MySecurityRealm
   │
   │ authenticate john
   ▼
Keycloak creates
signed SAMLResponse
   │
   ▼
Browser
   │
   │ POST
   ▼
http://localhost:8088/
Shibboleth.sso/SAML2/POST
   │
   ▼
Shibboleth
   │
   ├── validates response
   ├── verifies Keycloak signature
   ├── extracts uid
   ├── uid = john
   └── creates session
          │
          ▼
IIS
   │
   ▼
index.html
```

You should finally see:

> **Shibboleth Test Application**

---

# PART 25 — Inspect the authenticated session

After logging in, open:

```text
http://localhost:8088/Shibboleth.sso/Session
```

This is one of the most useful troubleshooting tools.

You want to see evidence of:

```text
Identity Provider:
https://localhost:9991/realms/MySecurityRealm

Session:
active

Attributes:
uid: john
```

The exact presentation can vary.

If authentication works but you don't see `uid`, troubleshoot:

```text
Keycloak username
       ↓
Keycloak SAML mapper
       ↓
SAML assertion
       ↓
attribute-map.xml
       ↓
Shibboleth uid
```

Don't troubleshoot Spring yet.

---

# PART 26 — Now introduce Spring Boot

Only after everything above works should we replace:

```text
IIS
 ↓
index.html
```

with:

```text
IIS
 ↓
Spring Boot
```

Your Spring application remains:

```text
http://localhost:9091
```

but users should access:

```text
http://localhost:8088/
```

The intended flow becomes:

```text
Browser
   │
   ▼
IIS :8088
   │
   ├────────► Shibboleth
   │               │
   │               ↕ SAML
   │               │
   │            Keycloak
   │
   │ authenticated
   │
   ▼
Reverse Proxy
   │
   ▼
Spring Boot :9091
```

---

# PART 27 — Remove SAML from Spring

Your Spring application no longer needs:

```text
spring-security-saml2-service-provider
```

Remove the SAML registration from `application.yml`.

For example, this entire section from your previous project goes away:

```yaml
spring:
  security:
    saml2:
      relyingparty:
        registration:
          keycloak:
            ...
```

And this goes away:

```java
.saml2Login(withDefaults())
```

Spring no longer needs:

```text
keycloak-metadata.xml
sp-private-key.key
sp-certificate.crt
```

Those belonged to Spring when Spring was the SP.

Now **Shibboleth owns the SP certificates and SAML configuration**.

---

# PART 28 — Keep Spring on an internal port

I recommend:

```yaml
server:
  address: 127.0.0.1
  port: 9091
```

This is important because we eventually want:

```text
Browser
   │
   ▼
IIS :8088
   │
   │ authenticated
   ▼
Spring :9091
```

and not:

```text
Browser ───────────────► Spring :9091
                         ↑
                       BYPASS
                         IIS
                         and
                     Shibboleth
```

Spring should trust identity only from the trusted IIS/Shibboleth path.

---

# PART 29 — IIS reverse proxy

This part is separate from Shibboleth itself.

For IIS to proxy:

```text
http://localhost:8088/
```

to:

```text
http://127.0.0.1:9091/
```

you normally use IIS reverse-proxy capabilities such as **URL Rewrite + Application Request Routing (ARR)**.

Before installing anything, check whether your IIS environment already has these components because your machine may require administrator privileges to add IIS extensions.

Once configured:

```text
Browser
GET http://localhost:8088/hello
           │
           ▼
IIS
           │
           │ Shibboleth says:
           │ authenticated = john
           │
           ▼
Reverse proxy
           │
           ▼
Spring

GET http://127.0.0.1:9091/hello
```

---

# PART 30 — Passing the identity to Spring

This deserves its own configuration step rather than simply turning on arbitrary headers.

Ultimately we want something like:

```text
Shibboleth

uid = john
email = john@example.com
       │
       ▼
IIS trusted authentication boundary
       │
       ▼
Spring

username = john
email = john@example.com
```

There are several ways to bridge the IIS/Shibboleth identity into a separate backend process. We should choose and configure that bridge explicitly rather than assuming IIS's `REMOTE_USER` automatically becomes embedded Tomcat's `request.getRemoteUser()` across an HTTP reverse proxy.

Most importantly, Spring must **not blindly trust a client-supplied header** such as:

```http
X-Authenticated-User: administrator
```

unless IIS strips/replaces it and Spring is inaccessible except through that trusted proxy.

We'll configure this bridge after the SAML/IIS portion is working.

---

# Your final file layout

Your project should eventually look roughly like:

```text
C:\Projects\Shibboleth_As_SP\
│
├── shibboleth-sp\
│   │
│   ├── etc\
│   │   └── shibboleth\
│   │       │
│   │       ├── shibboleth2.xml
│   │       ├── shibboleth2.xml.original
│   │       ├── attribute-map.xml
│   │       ├── attribute-map.xml.original
│   │       ├── attribute-policy.xml
│   │       ├── keycloak-metadata.xml
│   │       └── ...
│   │
│   ├── sbin64\
│   │   └── shibd.exe
│   │
│   ├── lib64\
│   │   └── ...
│   │
│   └── var\
│       └── log\
│
└── iis-test\
    └── index.html
```

Your Spring project can remain somewhere else; it does not need to live under the Shibboleth installation.

# The complete authentication picture

Once finished:

```text
                         ┌─────────────────────┐
                         │      Keycloak       │
                         │                     │
                         │ Realm:              │
                         │ MySecurityRealm     │
                         │                     │
                         │ :9991               │
                         └─────────▲───────────┘
                                   │
                                   │ SAML
                                   │
                                   ▼
┌─────────┐             ┌─────────────────────┐
│ Browser │────────────►│        IIS          │
└─────────┘             │       :8088         │
     ▲                   └──────────┬──────────┘
     │                              │
     │                        IIS Shibboleth
     │                            module
     │                              │
     │                              ▼
     │                   ┌─────────────────────┐
     │                   │   Shibboleth SP     │
     │                   │                     │
     │                   │ Entity ID:          │
     │                   │ http://localhost:   │
     │                   │ 8088/shibboleth     │
     │                   └──────────┬──────────┘
     │                              │
     │                       authenticated
     │                         user = john
     │                              │
     │                              ▼
     │                   ┌─────────────────────┐
     └───────────────────│    Spring Boot      │
                         │                     │
                         │ 127.0.0.1:9091      │
                         │                     │
                         │ NO SAML             │
                         └─────────────────────┘
```

### I would implement it in this exact order

1. **Finish the Shibboleth MSI installation with `Configure IIS support` checked.**

2. Verify `shibd.exe -check`.

3. Verify the Shibboleth IIS module.

4. Get the plain IIS site working at `http://localhost:8088`.

5. Configure `shibboleth2.xml`.

6. Get `/Shibboleth.sso/Metadata` working.

7. Create/import the Shibboleth client in Keycloak.

8. Get Keycloak login working.

9. Verify `/Shibboleth.sso/Session` and `uid=john`.

10. **Only then connect Spring Boot through IIS.**

This sequence will make troubleshooting much easier because if something fails at step 6, for example, we know Spring Boot has absolutely nothing to do with the problem.
