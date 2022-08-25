package run.halo.app.utils;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import run.halo.app.exception.QQZoneExporterException;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

@Slf4j
public class QQZoneExporter {
    private static final HttpHeaders headers = new HttpHeaders();

    static {
        headers.set("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        headers.set("accept-encoding", "gzip, deflate, br");
        headers.set("accept-language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.set("referer", "https://user.qzone.qq.com");
        headers.set("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36");
    }

    private QQZoneAccount account;
    private RestTemplate restTemplate;

    public QQZoneExporter(QQZoneAccount account, RestTemplate restTemplate) {
        this.account = account;
        this.restTemplate = restTemplate;
    }


    private static final Integer RETRY_TIMES = 3;

    private Boolean summaryInitialized = false;
    private Boolean canAccess = true;
    private Integer blogNum = 0;
    private Integer shuoShuoNum = 0;
    private Integer photoNum = 0;

    private static final String mainPage = "https://user.qzone.qq.com/proxy/domain/r.qzone.qq.com/cgi-bin/main_page_cgi";
    private static final String msgList = "https://user.qzone.qq.com/proxy/domain/taotao.qq.com/cgi-bin/emotion_cgi_msglist_v6";
    @Data
    public static class QQZoneAccount {
        @Pattern(regexp = "\\d+", message = "QQ号格式不正确")
        @NotBlank(message = "请输入用于登录的QQ号")
        private String selfUin;

        // 可选，为空则通过 cookies 中的 p_skey 计算
        private String gTk;

        @Pattern(regexp = "\\d+", message = "QQ号格式不正确")
        @NotBlank(message = "请输入需要导出数据的QQ号")
        private String targetUin;

        @NotBlank(message = "请输入 cookies")
        private String cookiesPair;

        private Boolean blog = false;
        private Boolean shuoshuo = false;
        private Boolean photo = false;
        private Boolean all = false;
    }

    // 获取日志、说说及相册的数量，并测试能否访问目标空间。
    public void getQQZoneSummary() {
        if (summaryInitialized)
            return;
        Map<String, Object> payload = new ImmutableMap.Builder<String, Object>()
            .put("uin", account.getSelfUin())
            .put("param", "16")
            .put("g_tk", account.getGTk())
            .build();
        JsonObject resp = doGet(mainPage, payload);
        int code = resp.get("code").getAsInt();
        if (code != 0) {
            log.error("can't access target uin: {}", resp.get("message").getAsString());
            canAccess = false;
            return;
        }

        JsonObject numberData = resp
            .get("data").getAsJsonObject()
            .get("module_16").getAsJsonObject();
        blogNum = numberData.get("data").getAsJsonObject().get("RZ").getAsInt();
        shuoShuoNum = numberData.get("data").getAsJsonObject().get("SS").getAsInt();
        photoNum  = numberData.get("data").getAsJsonObject().get("XC").getAsInt();

        log.info("Summary: 日志 - {}, 说说 - {}, 相册 - {}", blogNum, shuoShuoNum, photoNum);
    }

    public void exportShuoshuo() {
        if (!canAccess) {
            return;
        }
        getQQZoneSummary();
        int size = 40, loopNum = (int) Math.ceil(shuoShuoNum / size);
        Map<String, Object> payload = new HashMap<>() {
            {
                put("format","jsonp");
                put("need_private_comment","1");
                put("uin",account.getSelfUin());
                put("pos",0);
                put("num",size);
                put("param","16");
                put("g_tk",account.getGTk());
            }
        };

        for (int page = 0; page < loopNum; page ++) {
            int offset = page * size;
            int currentSize = page < loopNum - 1 ? size : shuoShuoNum - page * size;

            payload.put("pos", offset);
            payload.put("num", currentSize);

            JsonObject resp = doGet(msgList, payload);
            if (resp.get("msglist").isJsonNull()) {
                log.warn("msglist is null, page: {}, size: {}", page, currentSize);
                break;
            }
            log.info(resp.toString());
            break;
        }
    }

    private JsonObject doGet(String uri, Map<String, Object> uriParams) {
        headers.set("cookie", account.getCookiesPair());
        HttpEntity<String> entity = new HttpEntity<>("", headers);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(uri);
        for (Map.Entry<String, Object> entry : uriParams.entrySet()) {
            uriBuilder.queryParam(entry.getKey(), entry.getValue());
        }
        ResponseEntity<String> responseEntity =
            restTemplate.exchange(uriBuilder.build().toUri(), HttpMethod.GET, entity, String.class);
        String rawResp = responseEntity.getBody();
        if (!StringUtils.hasText(rawResp)) {
            throw new QQZoneExporterException("响应异常");
        }
        int start = rawResp.indexOf("{");
        int end = rawResp.lastIndexOf("}") + 1;
        return (JsonObject) JsonParser.parseString(rawResp.substring(start, end));
    }



}
