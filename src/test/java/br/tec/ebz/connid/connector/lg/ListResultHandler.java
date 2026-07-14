package br.tec.ebz.connid.connector.lg;

import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ResultsHandler;

import java.util.ArrayList;
import java.util.List;

public class ListResultHandler implements ResultsHandler {

    private final List<ConnectorObject> objects = new ArrayList<>();

    @Override
    public boolean handle(ConnectorObject connectorObject) {
        objects.add(connectorObject);
        return true;
    }

    public List<ConnectorObject> getObjects() {
        return objects;
    }
}
