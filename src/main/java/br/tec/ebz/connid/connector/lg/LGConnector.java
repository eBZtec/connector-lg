package br.tec.ebz.connid.connector.lg;

import br.tec.ebz.connid.connector.lg.processing.UserProcessing;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.*;

import java.util.List;

@ConnectorClass(displayNameKey = "lg.connector.display", configurationClass = LGConfiguration.class)
public class LGConnector implements Connector, TestOp, SearchOp<Filter>, SchemaOp {

    private static final Log LOG = Log.getLog(LGConnector.class);

    private LGConfiguration configuration;
    private LGConnection connection;
    private UserProcessing userProcessing;

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(Configuration configuration) {
        this.configuration = (LGConfiguration) configuration;
        this.connection = new LGConnection(this.configuration);

        this.userProcessing = new UserProcessing(this.connection, this.configuration);
    }

    @Override
    public void dispose() {
        configuration = null;
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
    }

    @Override
    public void test() {
        try {
            if (this.connection == null) {
                throw new ConnectionBrokenException("Connection is not initialized.");
            }
        } catch (InvalidAttributeValueException i) {
            throw new InvalidAttributeValueException("Invalid configuration: " + i.getMessage());
        } catch (Exception e) {
            throw new ConnectionFailedException("Connection test failed: " + e.getMessage());
        }
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return new FilterTranslator<Filter>() {
            @Override
            public List<Filter> translate(Filter filter) {
                return CollectionUtil.newList(filter);
            }
        };
    }

    @Override
    public void executeQuery(ObjectClass objectClass, Filter filter, ResultsHandler handler, OperationOptions options) {
        String id = null;
        try {
            if (filter != null) {
                id = filter.accept(new FilterHandler(objectClass), "");
                LOG.info("Query will be executed with the following filter: {0}", id);
                LOG.info("The object class from which the filter will be executed: {0}", objectClass.getDisplayNameKey());
            }
            if (ObjectClass.ACCOUNT.equals(objectClass)) {
                userProcessing.search(id, handler);
            } else {
                throw new UnsupportedOperationException("Unknown object class " + objectClass);
            }
        } catch (InvalidAttributeValueException e) {
            LOG.error("Failed to process search operation, reason {0}", e);
            throw new InvalidAttributeValueException("Failed to process search operation: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to process search operation, reason {0}", e);
            throw new ConnectorException("Failed to process search operation: " + e.getMessage());
        }
    }


    @Override
    public Schema schema() {
        SchemaBuilder schemaBuilder = new SchemaBuilder(LGConnector.class);

        schemaBuilder.defineObjectClass(userProcessing.userSchemaBuilder().build());

        return schemaBuilder.build();
    }
}
