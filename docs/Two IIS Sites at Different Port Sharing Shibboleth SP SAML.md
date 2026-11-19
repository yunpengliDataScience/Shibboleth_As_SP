**Let one application use IIS site port 8088 and another use IIS site port 8089, but both use the same Shibboleth SP SAML**



Yes. If your new LegacySpringApp IIS website is working on port `8088`, you can change its external port to 8089 while keeping the Spring Boot backend on 9093.

However, because you're using IIS + Shibboleth SP + Keycloak SAML, changing the external port affects more than IIS.

Your target configuration will be:

| Component               | URL                                             |
| ----------------------- | ----------------------------------------------- |
| Original ShibbolethDemo | `http://localhost:8088/`                        |
| New LegacySpringApp     | `http://localhost:8089/`                        |
| Spring Boot backend     | `http://127.0.0.1:9093/LegacySpringApp/`        |
| Keycloak IdP            | `https://localhost:9991/realms/MySecurityRealm` |

## 1. Change the IIS website binding

In IIS Manager:

1. Select Sites → LegacySpringApp.

2. Click Bindings… in the right Actions panel.

3. Select the existing HTTP binding on port `8088`.

4. Click Edit.

5. Change the port to `8089`.

6. Click OK, then Close.

Your original `ShibbolethDemo` site can remain on `8088`.

## 2. Update `shibboleth2.xml`

You previously added this mapping:

```
<Site id="2" name="localhost" scheme="http" port="8088"/>
<Site id="4" name="localhost" scheme="http" port="8088"/>
```

Change only Site ID 4:

```
<InProcess>
    <ISAPI normalizeRequest="true"
           safeHeaderNames="true"
           useVariables="true"
           useHeaders="true">

        <!-- Original ShibbolethDemo website -->
        <Site id="2"
              name="localhost"
              scheme="http"
              port="8088"/>

        <!-- New LegacySpringApp website -->
        <Site id="4"
              name="localhost"
              scheme="http"
              port="8089"/>

    </ISAPI>
</InProcess>
```

This assumes the new website's actual IIS Site ID is `4`.

### Update RequestMapper

Your existing RequestMapper protects `localhost:8088`.

Add another host for port `8089`:

```
<RequestMapper type="Native">
    <RequestMap>

        <!-- Original website -->
        <Host name="localhost"
              scheme="http"
              port="8088"
              authType="shibboleth"
              requireSession="true"/>

        <!-- New LegacySpringApp website -->
        <Host name="localhost"
              scheme="http"
              port="8089"
              authType="shibboleth"
              requireSession="true"/>

    </RequestMap>
</RequestMapper>
```

This makes Shibboleth require an authenticated session for both websites.

## 3. What about `web.config`?

You do not need to change the reverse-proxy destination.

Keep your working two-rule configuration:

```
<rewrite>
    <rules>

        <!-- Requests already containing the context path -->
        <rule name="LegacySpringApp Existing Context"
              stopProcessing="true">

            <match url="^LegacySpringApp(?:/.*)?$"
                   ignoreCase="true" />

            <action type="Rewrite"
                    url="http://127.0.0.1:9093/{R:0}"
                    appendQueryString="true" />
        </rule>

        <!-- Requests from the website root -->
        <rule name="LegacySpringApp Root Proxy"
              stopProcessing="true">

            <match url="^(.*)$" />

            <conditions>
                <add input="{URL}"
                     pattern="^/Shibboleth\.sso(?:/|$)"
                     negate="true" />
            </conditions>

            <action type="Rewrite"
                    url="http://127.0.0.1:9093/LegacySpringApp/{R:1}"
                    appendQueryString="true" />
        </rule>

    </rules>
</rewrite>
```

IIS uses the site binding to determine whether the browser connects on `8088` or `8089`. The rewrite rule only determines where IIS forwards the request.

## 4. Keycloak — important

Your existing Keycloak SAML client has:

```
Client ID: http://localhost:8088/shibboleth
Root URL:  http://localhost:8088
```

You have two options.

### Option A — Reuse the existing Keycloak client

Keep the existing Client ID and Shibboleth SP entity ID:

```
<ApplicationDefaults
    entityID="http://localhost:8088/shibboleth"
    REMOTE_USER="uid">
```

The Client ID does not have to match the current website port.

But you must ensure Keycloak accepts the new Shibboleth ACS endpoint, typically:

```
http://localhost:8089/Shibboleth.sso/SAML2/POST
```

Check the actual endpoint in Shibboleth's metadata before configuring it.

In Keycloak, review Clients → Shibboleth SP Demo → Settings and update or add the new ACS/processing URL as appropriate. Do not replace the old endpoint if `ShibbolethDemo` must continue working.

### Option B — Create a separate Keycloak client

If you want `ShibbolethDemo` and `LegacySpringApp` to have independent SAML configurations, create a separate SP identity and Keycloak client for the new website.

This requires configuring a separate Shibboleth application identity as well, not merely changing the Keycloak Client ID.

For now, I recommend Option A, because you're testing the same Shibboleth SP installation.

## 5. Restart and test

After changing `shibboleth2.xml`, validate the configuration using the installed Shibboleth tools, then restart the Shibboleth service and recycle the relevant IIS application pool if needed.

Test:

| URL                                             | Expected behavior                       |
| ----------------------------------------------- | --------------------------------------- |
| `http://localhost:8088/`                        | Original ShibbolethDemo site            |
| `http://localhost:8089/`                        | New LegacySpringApp site, requiring SSO |
| `http://localhost:8089/Shibboleth.sso/Metadata` | Shibboleth SP metadata                  |
| `http://localhost:8089/Shibboleth.sso/Session`  | Shibboleth session information          |
| `http://127.0.0.1:9093/LegacySpringApp/`        | Spring Boot backend, directly reachable |

Use a private browser window for testing so an existing SSO session does not hide authentication redirects.

### One important consideration

Your current `shibboleth2.xml` uses `cookieProps="https"` while your IIS endpoints use HTTP. This is worth reviewing if session persistence becomes unreliable. Keep secure cookies and HTTPS for production.

Also, your existing `shibboleth2.xml` has `redirectLimit="exact"`, so verify that the login flow returns to the correct public host and port.

Summary: Change the IIS binding, Site ID 4 mapping, and RequestMapper to port `8089`. Keep `web.config` and Spring Boot port `9093` unchanged. Then verify Keycloak accepts the new SAML ACS endpoint.

---------------------------------------

Your screenshot shows that Keycloak currently has:

Master SAML Processing URL

```
http://localhost:8088/Shibboleth.sso/SAML2/POST
```

You want both IIS websites to work:

| IIS website     | SAML ACS URL                                      |
| --------------- | ------------------------------------------------- |
| ShibbolethDemo  | `http://localhost:8088/Shibboleth.sso/SAML2/POST` |
| LegacySpringApp | `http://localhost:8089/Shibboleth.sso/SAML2/POST` |

### Can you add both URLs to Master SAML Processing URL?

No. That field accepts one URL, not a list.

To support both endpoints with the same Keycloak SAML client, you can configure additional allowed endpoints.

### Step 1 — Keep the existing Master SAML Processing URL

Leave it as:

```
http://localhost:8088/Shibboleth.sso/SAML2/POST
```

This preserves the original ShibbolethDemo configuration.

### Step 2 — Find Valid Redirect URIs

In Keycloak:

Clients → Shibboleth SP Demo → Settings → Access settings

Find Valid Redirect URIs.

If this field is available for your SAML client, add:

```
http://localhost:8088/Shibboleth.sso/*
http://localhost:8089/Shibboleth.sso/*
```

Keep the original URL if it is already configured.

Note that SAML ACS validation is not necessarily controlled by Valid Redirect URIs alone. The actual allowed ACS endpoints depend on your Keycloak version and SAML client configuration.

### Step 3 — Configure additional SAML ACS endpoints

For a SAML client, check whether your Keycloak version exposes additional ACS settings or supports multiple ACS endpoints through imported SP metadata.

If supported, register both exact ACS URLs:

```
http://localhost:8088/Shibboleth.sso/SAML2/POST
http://localhost:8089/Shibboleth.sso/SAML2/POST
```

Do not enter both URLs into the single Master SAML Processing URL field.

### My recommendation for your setup

Because you have two separate IIS websites, the most predictable configuration is to give each website its own SAML SP identity and Keycloak client.

For example:

| Setting                    | Original client                                   | New client                                        |
| -------------------------- | ------------------------------------------------- | ------------------------------------------------- |
| Client ID / SP Entity ID   | `http://localhost:8088/shibboleth`                | `http://localhost:8089/shibboleth`                |
| Master SAML Processing URL | `http://localhost:8088/Shibboleth.sso/SAML2/POST` | `http://localhost:8089/Shibboleth.sso/SAML2/POST` |
| Root URL                   | `http://localhost:8088`                           | `http://localhost:8089`                           |

Important: Creating a second Keycloak client is not sufficient by itself. Your current `shibboleth2.xml` has one shared `ApplicationDefaults` entity ID. To use two distinct SP identities, you would also need to configure separate Shibboleth applications and map the IIS websites to them.

For now, since both websites use the same Shibboleth SP installation, I would avoid changing the existing client until we confirm whether your Keycloak version supports the second ACS endpoint.

Could you scroll slightly upward in Keycloak and show the entire Access settings section, including any Valid Redirect URIs field? I can then tell you precisely what to enter without breaking the working port `8088` configuration.

---



Perfect. Your screenshot confirms that Keycloak supports multiple Valid redirect URIs.

You can add the new port `8089` while keeping the existing `8088` configuration.

## 1. Add the new ACS URL

Go to Keycloak → Clients → Shibboleth SP Demo → Settings → Access settings.

Under Valid redirect URIs, enter:

Existing URL — keep unchanged

[http://localhost:8088/Shibboleth.sso/SAML2/POST](http://localhost:8088/Shibboleth.sso/SAML2/POST?utm_source=chatgpt.com)

New URL — add

[http://localhost:8089/Shibboleth.sso/SAML2/POST](http://localhost:8089/Shibboleth.sso/SAML2/POST?utm_source=chatgpt.com)

Click Add valid redirect URIs, enter the second URL, and click Save.

Use these exact URLs rather than a wildcard, provided both match the ACS endpoints advertised by Shibboleth.

## 2. Keep the other fields unchanged

| Keycloak setting           | Value                                             |
| -------------------------- | ------------------------------------------------- |
| Client ID                  | `http://localhost:8088/shibboleth`                |
| Root URL                   | `http://localhost:8088`                           |
| Home URL                   | `http://localhost:8088`                           |
| Master SAML Processing URL | `http://localhost:8088/Shibboleth.sso/SAML2/POST` |

You don't need to change the Client ID or create another client if both IIS sites share the same Shibboleth SP entity ID.

For SP-initiated SSO, Keycloak can use the ACS URL supplied in the SAML authentication request, subject to its validation rules. The Master SAML Processing URL remains the default/fallback endpoint; it does not need to contain both URLs.

## 3. Test the new website

After saving, use a private browser window and visit:

`http://localhost:8089/`

Expected sequence:

1. IIS Site ID 4 receives the request.

2. Shibboleth redirects you to Keycloak.

3. You authenticate with Keycloak.

4. Keycloak sends the SAML response to `http://localhost:8089/Shibboleth.sso/SAML2/POST`.

5. Shibboleth establishes the session.

6. ARR forwards application requests to `http://127.0.0.1:9093/LegacySpringApp/`.

If Keycloak still sends the response to port `8088`, inspect the SAML AuthnRequest and Shibboleth metadata to determine which ACS endpoint is being selected.

One important distinction: Adding the second Valid redirect URI authorizes that destination; it does not force Keycloak to use it. The SP-initiated request must identify the appropriate ACS endpoint.

Your next step is simply to add the `8089` ACS URL, save, and test.
