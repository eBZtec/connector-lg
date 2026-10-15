**Conector LG**

Conector para integração do midPoint com o **LG**, voltado para **consulta e sincronização de usuários** (contratos de trabalho).

Ele não provisiona usuários no LG. Serve apenas para ler os dados existentes e trazer as alterações recentes para o midPoint via Live Sync.

**Este conector é somente leitura.** Não cria nem altera usuários no LG.

Uma característica importante deste conector é o suporte a **múltiplas contas por pessoa**. Um mesmo CPF pode ter mais de um contrato (matrícula) no LG. O conector expõe todos eles e usa o atributo `isPrimary` para indicar qual é o contrato principal.

---

## Recursos e funcionalidades

| Recurso               | Disponibilidade | Descrição                                                                               |
| --------------------- | --------------- | --------------------------------------------------------------------------------------- |
| **Schema**            | SIM             | Apenas usuários (contas).                                                               |
| **Live Sync**         | SIM             | Sincroniza alterações recentes de férias, afastamentos e rescisões.                     |
| **Password**          | NÃO             | Não gerencia senha.                                                                     |
| **Activation**        | NÃO             | Não ativa nem desativa usuários.                                                        |
| **Filtering**         | PARCIAL         | Só aceita filtro Equals no atributo `Name`/`Uid` (formato `matricula;codigoEmpresa`).   |
| **Native Attributes** | SIM             | Usa `Name` e `Uid` com o valor `matricula;codigoEmpresa`.                               |
| **Provisioning**      | NÃO             | Não cria, não altera e não exclui usuários. Apenas leitura + sincronização.             |
| **Múltiplas contas**  | SIM             | Permite várias projeções (contas) para o mesmo usuário, diferenciadas pelo `isPrimary`. |

---

## Como funciona o suporte a múltiplas contas (`isPrimary`)

No LG, uma mesma pessoa (mesmo CPF) pode ter mais de um contrato de trabalho.

O conector:

1. Busca todos os contratos.
2. Agrupa pelo CPF.
3. Define qual deles é o principal (`isPrimary = true`).
   - Prioriza o contrato ativo (sem data de rescisão).
   - Se todos estiverem rescindidos, marca como principal o que tem a data de rescisão mais recente.

No recurso do midPoint, isso é usado da seguinte forma:

- O objeto `UserType` é correlacionado pelo **CPF**.
- Cada contrato vira uma projeção (conta) diferente, identificada pelo `Name` (`matricula;codigoEmpresa`).
- A maioria dos atributos (nome, cargo, matrícula, empresa, unidade, datas etc.) só é mapeada para o usuário quando a conta for `isPrimary = true`.
- Assim o midPoint consegue manter várias contas ligadas ao mesmo usuário, mas usa apenas a principal para preencher os dados principais do focus.

Isso está configurado no `schemaHandling` do recurso (condições `basic.getAttributeValue(projection, "isPrimary")` nos inbounds e nas reações de sincronização).

---

## Atributos atuais

Atualmente o conector traz apenas **parte** dos atributos disponíveis na API do LG. Os atributos expostos hoje são:

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
- `isPrimary` (boolean — indica se é o contrato principal daquele CPF)

**Atributos preenchidos principalmente no Live Sync:**

- `DataDaRescisao`
- `DataInicioFerias` / `DataFimFerias`
- `DataInicioAfastamento` / `DataFimAfastamento`

### Melhoria futura

A inclusão de mais atributos está prevista como melhoria futura. Hoje o conector não mapeia todos os campos que a API do LG disponibiliza.

---

## Configuração do Recurso

| Atributo      | Obrigatório | Descrição                                                 |
| ------------- | ----------- | --------------------------------------------------------- |
| `urlEndpoint` | SIM         | Endereço base da API. Exemplo: `https://api.lg.com.br`    |
| `usuario`     | SIM         | Usuário utilizado na autenticação SOAP.                   |
| `senha`       | SIM         | Senha do usuário.                                         |
| `guidtenant`  | SIM         | Identificador do tenant (GuidTenant).                     |
| `ambiente`    | SIM         | Código do ambiente.                                       |
| `empresas`    | SIM         | Códigos das empresas separados por vírgula (ex: `1,2,5`). |

O campo `empresas` é importante porque o Live Sync consulta as alterações empresa por empresa.

---

## Limitações atuais

- Não cria, altera ou exclui usuários no LG.
- Só traz uma parte dos atributos disponíveis na API.
- O filtro de busca só funciona com Equals no formato `matricula;codigoEmpresa`.
- Atributos de férias, afastamento e rescisão são preenchidos principalmente durante o Live Sync.
- A sincronização consulta um intervalo curto de datas (basicamente o dia anterior até o dia seguinte).

---

## Compilação

```bash
mvn clean install -DskipTests=true
```

O arquivo gerado fica em `target/connector-lg-{version}.jar`.

---

## Instalação

Copie o JAR para:

```text
{midPoint_home}/icf-connectors/
```

ou

```text
{midPoint_home}/connid-connectors/
```

---

## Certificado SSL

Se o midPoint não conseguir se conectar na API do LG por causa de erro de certificado (erro de SSL/TLS, “unable to find valid certification path”, “PKIX path building failed” etc.), é necessário importar o certificado da API no keystore do midPoint.

### 1. Baixar o certificado da API

Execute no servidor (ou em qualquer máquina que consiga acessar a API):

```bash
openssl s_client -connect api.lg.com.br:443 -servername api.lg.com.br </dev/null \
| sed -ne '/BEGIN CERTIFICATE/,/END CERTIFICATE/p' > lg-api.crt
```

Esse comando:
- Conecta na API do LG na porta 443.
- Extrai o certificado que o servidor apresenta.
- Salva o certificado no arquivo `lg-api.crt`.

Verifique se o arquivo foi criado corretamente:

```bash
cat lg-api.crt
```

Ele deve começar com `-----BEGIN CERTIFICATE-----` e terminar com `-----END CERTIFICATE-----`.

### 2. Copiar o certificado para o servidor do midPoint

Se você gerou o arquivo em outro servidor, copie o `lg-api.crt` para o servidor onde o midPoint está instalado (por exemplo, para `/opt/` ou para o diretório home do usuário do midPoint).

Exemplo:

```bash
scp lg-api.crt usuario@servidor-midpoint:/opt/lg-api.crt
```

### 3. Descobrir a senha do keystore

A senha do keystore fica no arquivo de configuração do midPoint. Normalmente:

```bash
cat /opt/midpoint/var/config.xml
```

Procure a tag que contém a senha do keystore (geralmente algo como `<keystore>` ou `keyStorePassword`). Anote essa senha, pois ela será usada no próximo comando.

### 4. Importar o certificado no keystore do midPoint

No servidor do midPoint, execute (ajuste o caminho do keystore, do certificado e a senha):

```bash
keytool -keystore /caminho/completo/keystore.jceks -storetype jceks -storepass SENHA_KEYSTORE -import -alias "Lg_api" -trustcacerts -file /opt/lg-api.crt
```

Substitua:
- `/caminho/completo/keystore.jceks` → pelo caminho real do arquivo keystore do midPoint
- `SENHA_KEYSTORE` → pela senha encontrada no `config.xml`
- `/opt/lg-api.crt` → pelo caminho real onde você colocou o certificado

Quando o keytool perguntar se deseja confiar no certificado, digite `yes`.

### 5. Reiniciar o midPoint

Após importar o certificado, reinicie o midPoint para que ele carregue o novo truststore:

```bash
# Exemplo (pode variar conforme a instalação)
systemctl restart midpoint
# ou
/opt/midpoint/bin/midpoint.sh restart
```

### Observações

- O arquivo `keystore.jceks` geralmente fica no diretório de instalação do midPoint (muitas vezes em `/opt/midpoint/var/` ou no diretório de configuração). Se não estiver no diretório atual, use sempre o caminho completo no comando do `keytool`.
- Se o comando `keytool` não for encontrado, verifique se o Java está instalado e se o `bin` do Java está no `PATH`.
- O alias (`Lg_api`) pode ser qualquer nome, desde que seja único no keystore.

Depois desses passos o midPoint deve conseguir se conectar na API do LG sem erro de certificado.

---

## Resumo

O conector LG permite que o midPoint **leia e sincronize** usuários do LG, com suporte a **múltiplas contas por pessoa**.

Hoje ele consegue:

- Consultar todos os usuários das empresas configuradas
- Consultar um usuário específico
- Trazer alterações recentes via Live Sync (férias, afastamentos e rescisões)
- Expor várias contas para o mesmo CPF e indicar qual é a principal (`isPrimary`)
- Permitir que o recurso do midPoint mantenha múltiplas projeções e use apenas a principal para preencher os dados do usuário

Ele **não** provisiona usuários e, no momento, **não traz todos os atributos** disponíveis na API do LG. A ampliação dos atributos está prevista como melhoria futura.