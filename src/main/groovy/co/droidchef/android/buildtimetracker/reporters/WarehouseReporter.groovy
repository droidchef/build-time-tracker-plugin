package co.droidchef.android.buildtimetracker.reporters

import co.droidchef.android.buildtimetracker.Timing
import com.google.gson.Gson
import okhttp3.*
import org.gradle.BuildResult
import org.gradle.api.logging.Logger

import javax.net.ssl.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.text.SimpleDateFormat

class WarehouseReporter extends AbstractBuildTimeTrackerReporter {

    def data = []
    def metaData = []
    def debugHttp = false

    WarehouseReporter(HashMap<String, String> options, Logger logger) {
        super(options, logger)
    }

    @Override
    def run(List<Timing> timings) {
        debugHttp = getOption("debugHttp", "false").toBoolean()
        long timestamp = new TrueTimeProvider().getCurrentTime()
        TimeZone tz = TimeZone.getTimeZone("UTC")
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'")
        df.setTimeZone(tz)

        def info = new SysInfo()
        def osId = info.getOSIdentifier()
        def cpuId = info.getCPUIdentifier()
        def maxMem = info.getMaxMemory()
        def userName = System.getProperty("user.name")

        def measurements = []

        timings.eachWithIndex { it, index ->
            logger.lifecycle(it.path)
            measurements << [
                    timestamp: timestamp,
                    order: index,
                    task: it.path,
                    success: it.success,
                    did_work: it.didWork,
                    skipped: it.skipped,
                    ms: it.ms,
                    date: df.format(new Date(timestamp)),
            ]
        }

        metaData = [
                cpu: cpuId,
                memory: maxMem,
                os: osId,
                username: userName
        ]

        data = [
                success: timings.every { it.success },
                count: timings.size(),
                meta: metaData,
                measurements: measurements,
        ]
    }

    private void postDataToWarehouseWithOkHttp(data, boolean debugHttp) {
        def url = getOption("url", null)

        SSLContext sc = SSLContext.getInstance("SSL")
        sc.init(null, [new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { null }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {}

            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
        }] as TrustManager[], new SecureRandom())
        def sf = sc.getSocketFactory()


        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        OkHttpClient client = new OkHttpClient.Builder()
        .sslSocketFactory(sf, new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { new X509Certificate[0] }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {}

            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
        })
        .hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        })
        .build()

        Gson gson = new Gson()
        def json = gson.toJson(data)

        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try {
            Response response = client.newCall(request).execute()
            Map jsonReponse = gson.fromJson(response.body().charStream(), Map.class)
            if (response.successful) {
                logger.quiet 'Build stats reported'
                if (debugHttp) {
                    logger.quiet jsonReponse.toMapString()
                }
            } else {
                logger.quiet 'Failed to report Build stats!'
            }
        } catch (IOException e) {
            logger.quiet e.toString()
        }
    }
    @Override
    void onBuildResult(BuildResult result) {

        def taskList = result.gradle.startParameter.taskRequests

        def firstTaskName = "undefined"

        if (taskList != null && !taskList.isEmpty()) {
            def arguments = taskList.get(0).args
            if (arguments.size() > 0) {
                firstTaskName = arguments.get(0)
            }
        }

        metaData.put("firstTaskName", firstTaskName)

        // write to server
        postDataToWarehouseWithOkHttp(data, debugHttp)
    }
}