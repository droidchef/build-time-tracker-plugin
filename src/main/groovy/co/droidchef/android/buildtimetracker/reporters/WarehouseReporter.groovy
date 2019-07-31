package co.droidchef.android.buildtimetracker.reporters

import co.droidchef.android.buildtimetracker.Timing
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.ssl.SSLSocketFactory
import org.gradle.BuildResult
import org.gradle.api.logging.Logger

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
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

    private void postDataToWarehouse(data, boolean debugHttp) {
        def url = getOption("url", null)
        def http = new HTTPBuilder(url)
        http.getClient().getParams().setParameter("http.connection.timeout", new Integer(5000))
        http.getClient().getParams().setParameter("http.socket.timeout", new Integer(5000))
        try {
            SSLContext sc = SSLContext.getInstance("SSL")
            sc.init(null, [new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { null }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {}

                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }] as TrustManager[], new SecureRandom())
            def sf = new SSLSocketFactory(sc, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)
            def httpsScheme = new Scheme("https", sf, 443)
            http.client.connectionManager.schemeRegistry.register(httpsScheme)


            http.request(Method.POST, ContentType.JSON) { req ->
                body = data

                //headers.put(getOption('headerParamKey1', null), getOption('headerParamVal1', null))
                headers.Accept = 'application/json'

                response.success = { resp, json ->
                    if (debugHttp) {
                        logger.lifecycle(json)
                    }
                    logger.quiet 'Build stats reported'
                }
                response.failure = { resp ->
                    if (debugHttp) {
                        logger.lifecycle resp.toString()
                    }
                    logger.quiet 'Failed to report build stats!'
                }
            }
        } catch (Exception exception) {
            logger.quiet sprintf('Failed to report build stats! %1$s', exception.toString())
            logger.lifecycle(exception.stackTrace.toString())
            //logger.quiet sprintf(data.toMapString())
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
        postDataToWarehouse(data, debugHttp)
    }
}