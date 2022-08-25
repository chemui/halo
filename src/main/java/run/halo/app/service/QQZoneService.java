package run.halo.app.service;

import run.halo.app.utils.QQZoneExporter;

/**
 * QQZone exporter service interface.
 *
 * @author feivxs
 * @date 2021-06-27
 */
public interface QQZoneService {
    /**
     * Export contents from QQZone as journals
     */
    void export(QQZoneExporter.QQZoneAccount param);
}
