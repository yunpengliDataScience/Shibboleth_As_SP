Yes. Since IIS is already enabled, you can install the **Shibboleth Native SP for Windows** and let its installer integrate with IIS automatically.

One important current detail: as of September 2026, **Shibboleth SP 3.6.0 is the current stable release**. Shibboleth 4 is still alpha, so use **3.6.0**, not V4, for this exercise. ([Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/overview?homepageId=2058387896&utm_source=chatgpt.com "Home - Service Provider 3 - Confluence"))

## 1. Download Shibboleth SP

For a normal 64-bit Windows machine, download:

**`shibboleth-sp-3.6.0.0-win64.msi`**

from the official Shibboleth download site. ([Shibboleth Consortium](https://shibboleth.net/downloads/service-provider/latest/win64/?utm_source=chatgpt.com "Index of /downloads/service-provider/latest/win64"))

[Official Shibboleth SP 3.6.0 Windows 64-bit download](https://shibboleth.net/downloads/service-provider/latest/win64/?utm_source=chatgpt.com)

Don't use the 32-bit version unless your Windows installation itself is 32-bit. Shibboleth provides both, but a 64-bit installation can support both 32- and 64-bit IIS application pools. ([Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2065335360/IIS?atl_f=content-tree&utm_source=chatgpt.com "IIS - Service Provider 3 - Confluence"))

---

## 2. Run the MSI installer

Run the MSI **as Administrator**.

This is important because Shibboleth needs to:

```text
Install Shibboleth files
        ↓
Install shibd Windows service
        ↓
Install Shibboleth IIS native module
        ↓
Configure environment
```

For IIS 7 and later, current Shibboleth SP uses:

```text
iis7_shib.dll
```

rather than the old:

```text
isapi_shib.dll
```

The installer detects IIS and normally configures this integration automatically. ([Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2065335360/IIS?atl_f=content-tree&utm_source=chatgpt.com "IIS - Service Provider 3 - Confluence"))

If your corporate PC requires administrator credentials for MSI installation, you'll need IT/admin assistance even though IIS itself is already enabled.

---

## 3. Restart Windows

I recommend rebooting after installation.

Shibboleth's documentation specifically notes that environment/path changes can require a reboot before diagnosing IIS startup problems. ([Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2065335360/IIS?atl_f=content-tree&utm_source=chatgpt.com "IIS - Service Provider 3 - Confluence"))

After restarting, you'll typically have an installation resembling:

```text
C:\opt\shibboleth-sp\
│
├── etc\
│   └── shibboleth\
│       ├── shibboleth2.xml       ← MAIN CONFIG
│       ├── attribute-map.xml
│       ├── attribute-policy.xml
│       ├── native.logger
│       └── ...
│
├── sbin64\
│   └── shibd.exe
│
├── lib64\
│   └── shibboleth\
│       └── iis7_shib.dll
│
└── var\
    └── log\
        └── shibboleth\
```

Exact paths can vary depending on the installation location. ([Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2063695920?utm_source=chatgpt.com "Configuration - Service Provider 3 - Confluence"))

---

## 4. Check the Shibboleth service

Press:

```text
Win + R
```

enter:

```text
services.msc
```

Look for the Shibboleth service.

Or from Command Prompt:

```bat
sc query shibd_default
```

Depending on the installation/service naming, you can also inspect Services for `Shibboleth`.

You want the service to be:

```text
RUNNING
```

Conceptually, you now have two pieces:

```text
IIS
 │
 │ iis7_shib.dll
 │
 ▼
Shibboleth
shibd.exe
```

This distinction is important.

`shibd.exe` isn't the web server. IIS receives the HTTP requests; the Shibboleth IIS module communicates with `shibd`.

---

## 5. Validate the default Shibboleth configuration

This is an especially useful command.

Open an Administrator Command Prompt and go to your Shibboleth installation.

For example:

```bat
cd C:\opt\shibboleth-sp\sbin64
```

Run:

```bat
shibd.exe -check
```

Shibboleth specifically recommends `shibd -check` for diagnosing configuration/startup problems. ([Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2065335360/IIS?atl_f=content-tree&utm_source=chatgpt.com "IIS - Service Provider 3 - Confluence"))

If everything is okay, you should get a message indicating the configuration is valid.

This command becomes very useful later when we edit:

```text
shibboleth2.xml
```

Always run:

```bat
shibd.exe -check
```

before restarting Shibboleth after configuration changes.

---

## 6. Verify the Shibboleth IIS module

Open:

```text
IIS Manager
```

You can press Win+R and enter:

```text
inetmgr
```

Select the server in the left-hand panel and look at:

**Modules**

You should find a Shibboleth native module corresponding to the installed `iis7_shib.dll`.

Current Shibboleth installers automatically configure this module when IIS 7+ is detected. ([Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2065335360/IIS?atl_f=content-tree&utm_source=chatgpt.com "IIS - Service Provider 3 - Confluence"))

So normally you should **not manually install the old ISAPI filter** instructions you may find in older tutorials.

---

# 7. Understand what you have installed

At this point we have only installed the infrastructure.

Your architecture is:

```text
                       Keycloak
                      NOT CONNECTED
                           YET
                            │
                            │
                            │
Browser ────────► IIS
                  │
                  │ iis7_shib.dll
                  ▼
              Shibboleth SP
                  │
                  ▼
                shibd
```

We have **not yet connected Keycloak**.

And we have **not yet connected Spring Boot**.

That's intentional. I recommend getting each layer working separately rather than configuring everything at once.

---

# 8. Next: configure the Shibboleth SP identity

The most important file is:

```text
C:\opt\shibboleth-sp\etc\shibboleth\shibboleth2.xml
```

Inside it we'll configure something like:

```xml
<ApplicationDefaults
    entityID="https://localhost/shibboleth"
    REMOTE_USER="uid">

    ...

</ApplicationDefaults>
```

This means:

```text
Shibboleth SP Entity ID
        =
https://localhost/shibboleth
```

Remember our earlier terminology:

```text
Your old architecture:

Spring Boot
   =
SAML SP
   =
Keycloak Client


New architecture:

Shibboleth
   =
SAML SP
   =
Keycloak Client


Spring Boot
   =
ordinary application
```

---

# 9. Then connect Keycloak

We'll configure:

```text
                    Keycloak
                       │
                       │ SAML
                       │
                       ▼
                 Shibboleth SP
                       │
                       │ authenticated
                       │ username
                       ▼
                      IIS
                       │
                       ▼
                 Spring Boot
```

The SAML exchange will be entirely:

```text
Keycloak ←──── SAML ────→ Shibboleth
```

**not**

```text
Keycloak ←── SAML ──→ Spring Boot
```

That gives you exactly the separation you wanted.

### I recommend stopping here first

Install **Shibboleth SP 3.6.0 64-bit**, reboot Windows, then check these three things:

```text
1. IIS Manager opens

2. Shibboleth service is running

3. shibd.exe -check
   reports valid configuration
```

Once those three are working, the next step is to configure **`shibboleth2.xml` specifically for your existing Keycloak `MySecurityRealm` on port `9991`**. I can walk you through that file line-by-line rather than using a generic Shibboleth configuration.


