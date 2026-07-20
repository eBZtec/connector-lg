package br.tec.ebz.connid.connector.lg;

import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;

import java.util.ArrayList;
import java.util.List;

public class ListSyncResultHandler implements SyncResultsHandler {

    private List<SyncDelta> objects = new ArrayList<>();

    @Override
    public boolean handle(SyncDelta syncDelta) {
        objects.add(syncDelta);
        return true;
    }

    public List<SyncDelta> getObjects(){ return objects; }
}
