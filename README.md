**LG Connector**

Connector for integrating midPoint with **LG**, focused on **querying and synchronizing users** (employment contracts).

It does not provision users in LG. It is only used to read existing data and bring recent changes into midPoint via Live Sync.

**This connector is read-only.** It does not create or modify users in LG.

An important feature of this connector is support for **multiple accounts per person**. The same CPF can have more than one contract (employee ID / *matrícula*) in LG. The connector exposes all of them and uses the `isPrimary` attribute to indicate which is the primary contract.

---

## Resources and Features

| Resource              | Availability | Description                                                                              |
| --------------------- | ------------ | ---------------------------------------------------------------------------------------- |
| **Schema**            | YES          | Users (accounts) only.                                                                   |
| **Live Sync**         | YES          | Synchronizes recent changes related to vacations, leaves of absence, and terminations.   |
| **Password**          | NO           | Does not manage passwords.                                                               |
| **Activation**        | NO           | Does not activate or deactivate users.                                                   |
| **Filtering**         | PARTIAL      | Only accepts Equals filter on the `Name`/`Uid` attribute (format `matricula;codigoEmpresa`). |
| **Native Attributes** | YES          | Uses `Name` and `Uid` with the value `matricula;codigoEmpresa`.                          |
| **Provisioning**      | NO           | Does not create, update, or delete users. Read + synchronization only.                   |
| **Multiple accounts** | YES          | Allows multiple projections (accounts) for the same user, differentiated by `isPrimary`. |

---

## How multiple-account support works (`isPrimary`)

In LG, the same person (same CPF) can have more than one employment contract.

The connector:

1. Retrieves all contracts.
2. Groups them by CPF.
3. Determines which one is the primary (`isPrimary = true`).
   - Prioritizes the active contract (no termination date).
   - If all contracts are terminated, it marks as primary the one with the most recent termination date.

In the midPoint resource, this is used as follows:

- The `UserType` object is correlated by **CPF**.
- Each contract becomes a different projection (account), identified by `Name` (`matricula;codigoEmpresa`).
- Most attributes (name, position, employee ID, company, organizational unit, dates, etc.) are only mapped to the user when the account has `isPrimary = true`.
- This way midPoint can keep multiple accounts linked to the same user, but uses only the primary one to fill the main data of the focus.

This is configured in the resource’s `schemaHandling` (conditions using `basic.getAttributeValue(projection, "isPrimary")` on the inbounds and synchronization reactions).

---

## Current Attributes

Currently the connector brings only **part** of the attributes available in the LG API. The attributes exposed today are:

- `Name` / `Uid` → `matricula;codigoEmpresa`
- `Matricula`
- `Nome`
- `Cargo`
- `Empresa`
- `UnidadeOrganizacional`
- `DataAdmissao`
- `DataDeNascimento`
- `CPF`
- `GestorMatricula`
- `EmailCorporativo`
- `isPrimary` (boolean — indicates whether this is the primary contract for that CPF)

**Attributes mainly populated during Live Sync:**

- `DataDaRescisao`
- `DataInicioFerias` / `DataFimFerias`
- `DataInicioAfastamento` / `DataFimAfastamento`

### Future Improvement

Adding more attributes is planned as a future improvement. Today the connector does not map all fields provided by the LG API.

---

## Resource Configuration

| Attribute     | Required | Description                                                  |
| ------------- | -------- | ------------------------------------------------------------ |
| `urlEndpoint` | YES      | Base URL of the API. Example: `https://api.lg.com.br`        |
| `usuario`     | YES      | Username used for SOAP authentication.                       |
| `senha`       | YES      | User password.                                               |
| `guidtenant`  | YES      | Tenant identifier (GuidTenant).                              |
| `ambiente`    | YES      | Environment code.                                            |
| `empresas`    | YES      | Company codes separated by comma (e.g. `1,2,5`).             |

The `empresas` field is important because Live Sync queries changes company by company.

---

## Current Limitations

- Does not create, update, or delete users in LG.
- Only brings a subset of the attributes available in the API.
- Search filter only works with Equals in the format `matricula;codigoEmpresa`.
- Vacation, leave, and termination attributes are mainly filled during Live Sync.
- Synchronization queries a short date range (basically from the previous day to the next day).

---

## Build

```bash
mvn clean install -DskipTests=true
```

The generated file is located at `target/connector-lg-{version}.jar`.

---

## Installation

Copy the JAR to:

```text
{midPoint_home}/icf-connectors/
```

or

```text
{midPoint_home}/connid-connectors/
```

---

## SSL Certificate

If midPoint cannot connect to the LG API because of a certificate error (SSL/TLS error, “unable to find valid certification path”, “PKIX path building failed”, etc.), you need to import the API certificate into midPoint’s keystore.

### 1. Download the API certificate

Run on the server (or on any machine that can reach the API):

```bash
openssl s_client -connect api.lg.com.br:443 -servername api.lg.com.br </dev/null \
| sed -ne '/BEGIN CERTIFICATE/,/END CERTIFICATE/p' > lg-api.crt
```

This command:
- Connects to the LG API on port 443.
- Extracts the certificate presented by the server.
- Saves the certificate to the file `lg-api.crt`.

Verify that the file was created correctly:

```bash
cat lg-api.crt
```

It should start with `-----BEGIN CERTIFICATE-----` and end with `-----END CERTIFICATE-----`.

### 2. Copy the certificate to the midPoint server

If you generated the file on another server, copy `lg-api.crt` to the server where midPoint is installed (for example, to `/opt/` or to the midPoint user’s home directory).

Example:

```bash
scp lg-api.crt usuario@servidor-midpoint:/opt/lg-api.crt
```

### 3. Find the keystore password

The keystore password is in the midPoint configuration file. Usually:

```bash
cat /opt/midpoint/var/config.xml
```

Look for the tag that contains the keystore password (usually something like `<keystore>` or `keyStorePassword`). Note this password, as it will be used in the next command.

### 4. Import the certificate into midPoint’s keystore

On the midPoint server, run (adjust the keystore path, certificate path, and password):

```bash
keytool -keystore /full/path/to/keystore.jceks -storetype jceks -storepass KEYSTORE_PASSWORD -import -alias "Lg_api" -trustcacerts -file /opt/lg-api.crt
```

Replace:
- `/full/path/to/keystore.jceks` → with the real path of midPoint’s keystore file
- `KEYSTORE_PASSWORD` → with the password found in `config.xml`
- `/opt/lg-api.crt` → with the real path where you placed the certificate

When keytool asks whether you want to trust the certificate, type `yes`.

### 5. Restart midPoint

After importing the certificate, restart midPoint so it loads the new truststore:

```bash
# Example (may vary depending on the installation)
systemctl restart midpoint
# or
/opt/midpoint/bin/midpoint.sh restart
```

### Notes

- The `keystore.jceks` file is usually located in the midPoint installation directory (often in `/opt/midpoint/var/` or in the configuration directory). If it is not in the current directory, always use the full path in the `keytool` command.
- If the `keytool` command is not found, check that Java is installed and that the Java `bin` directory is in the `PATH`.
- The alias (`Lg_api`) can be any name, as long as it is unique in the keystore.

After these steps, midPoint should be able to connect to the LG API without certificate errors.

---

## Summary

The LG connector allows midPoint to **read and synchronize** users from LG, with support for **multiple accounts per person**.

Today it can:

- Query all users of the configured companies
- Query a specific user
- Bring recent changes via Live Sync (vacations, leaves of absence, and terminations)
- Expose multiple accounts for the same CPF and indicate which is the primary one (`isPrimary`)
- Allow the midPoint resource to maintain multiple projections and use only the primary one to fill user data

It **does not** provision users and, at the moment, **does not bring all attributes** available in the LG API. Expanding the attributes is planned as a future improvement.
