package run.halo.app.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import run.halo.app.exception.QQZoneExporterException;
import run.halo.app.service.QQZoneService;
import run.halo.app.utils.QQZoneExporter;

@Service
public class QQZoneServiceImpl implements QQZoneService {
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void export(QQZoneExporter.QQZoneAccount param) {
        if (!StringUtils.hasText(param.getGTk())) {
            param.setGTk(calculateGTk(param.getCookiesPair()));
            if (!StringUtils.hasText(param.getGTk())) {
                throw new QQZoneExporterException("从Cookie中解析gTk失败");
            }
        }
        QQZoneExporter exporter = new QQZoneExporter(param, restTemplate);
        exporter.getQQZoneSummary();

        exporter.exportShuoshuo();
    }

    private String calculateGTk(String cookiesPair) {
        String key = "p_skey=";
        int pos = cookiesPair.indexOf(key);
        int start = pos + key.length();
        int end = cookiesPair.indexOf(";", pos);
        String val = cookiesPair.substring(start, end);

        int t = 5381;
        for (char ch : val.toCharArray()) {
            t += (t << 5) + ch;
        }
        return String.valueOf(t & 2147483647);
    }
}
