package br.tec.ebz.connid.connector.lg.user;

import br.tec.ebz.connid.connector.lg.BaseTestConnection;
import br.tec.ebz.connid.connector.lg.ListSyncResultHandler;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.util.List;

public class SyncOpTest extends BaseTestConnection {

    private static final Log LOG = Log.getLog(SyncToken.class);

    @Test(groups = "Sync", priority = 1)
    public void deveSincronizarUsuarios(){
        ConnectorFacade facade = setupConnector();
        ListSyncResultHandler listSyncResultHandler = new ListSyncResultHandler();
        facade.sync(ObjectClass.ACCOUNT, null, listSyncResultHandler, null);

        List<SyncDelta> objects = listSyncResultHandler.getObjects();
        //AssertJUnit.assertFalse(objects.isEmpty());

        LOG.info("Token da ultima listagem: {0}", objects);
        if (!objects.isEmpty()){
            SyncDelta delta = objects.get(0);
            SyncToken token = delta.getToken();

            AssertJUnit.assertNotNull(token);
            LOG.info("Token da ultima listagem: {0}", token.getValue().toString());
        }
    }
}
