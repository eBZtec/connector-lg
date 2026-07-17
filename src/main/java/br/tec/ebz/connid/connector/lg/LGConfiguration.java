package br.tec.ebz.connid.connector.lg;

import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class LGConfiguration extends AbstractConfiguration {

    private static final Log LOG = Log.getLog(LGConfiguration.class);

    private String urlEndpoint;
    private String usuario;
    private GuardedString senha;
    private String guidtenant;
    private String ambiente;
    private String empresas;

    @ConfigurationProperty(
            displayMessageKey = "lg.config.urlEndpoint",
            helpMessageKey = "lg.config.urlEndpoint.help",
            order = 1,
            required = true
    )
    public String getUrlEndpoint() {
        return urlEndpoint;
    }

    public void setUrlEndpoint(String urlEndpoint) {
        this.urlEndpoint = urlEndpoint;
    }

    @ConfigurationProperty(
            displayMessageKey = "lg.config.usuario",
            helpMessageKey = "lg.config.usuario.help",
            required = true,
            order=1)
    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    @ConfigurationProperty(
            displayMessageKey = "lg.config.senha",
            helpMessageKey = "lg.config.senha.help",
            order = 2,
            required = true
    )
    public GuardedString getSenha() {
        return senha;
    }

    public void setSenha(GuardedString senha) {
        this.senha = senha;
    }

    @ConfigurationProperty(
            displayMessageKey = "lg.config.guidtenant",
            helpMessageKey = "lg.config.guidtenant.help",
            required = true,
            order=1)
    public String getGuidtenant() {
        return guidtenant;
    }

    public void setGuidtenant(String guidtenant) {
        this.guidtenant = guidtenant;
    }

    @ConfigurationProperty(
            displayMessageKey = "lg.config.ambiente",
            helpMessageKey = "lg.config.ambiente.help",
            required = true,
            order=1)
    public String getAmbiente() {
        return ambiente;
    }

    public void setAmbiente(String ambiente) {
        this.ambiente = ambiente;
    }

    @ConfigurationProperty(
            displayMessageKey = "lg.config.empresas",
            helpMessageKey = "lg.config.empresas.help",
            required = true,
            order=1)
    public String getEmpresas() {
        return empresas;
    }

    public void setEmpresas(String empresas) {
        this.empresas = empresas;
    }

    @Override
    public void validate() {
        if (urlEndpoint == null || urlEndpoint.isEmpty())
            throw new IllegalArgumentException("UrlEndpoint is required.");
        if (usuario == null || usuario.isEmpty())
            throw new IllegalArgumentException("Usuario is required.");
        if (senha == null)
            throw new IllegalArgumentException("Senha is required.");
        if (guidtenant == null || guidtenant.isEmpty())
            throw new IllegalArgumentException("Guidtenant is required.");
        if (ambiente == null || ambiente.isEmpty())
            throw new IllegalArgumentException("Ambiente is required.");
        if (empresas == null || empresas.isEmpty())
            throw new IllegalArgumentException("empresas is required.");
    }
}