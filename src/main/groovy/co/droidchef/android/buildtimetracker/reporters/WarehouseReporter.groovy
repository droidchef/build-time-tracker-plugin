package co.droidchef.android.buildtimetracker.reporters

import co.droidchef.android.buildtimetracker.Timing
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.gradle.api.logging.Logger

import java.text.DateFormat
import java.text.SimpleDateFormat

class WarehouseReporter extends AbstractBuildTimeTrackerReporter {
    WarehouseReporter(HashMap<String, String> options, Logger logger) {
        super(options, logger)
    }

    @Override
    def run(List<Timing> timings) {

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

        def data = [
                success: timings.every { it.success },
                count: timings.size(),
                meta: [
                        cpu: cpuId,
                        memory: maxMem,
                        os: osId,
                        user_name: userName
                ],
                measurements: measurements,
        ]
        // write to server
        def url = getOption("url", null)
        def http = new HTTPBuilder(url)
        http.getClient().getParams().setParameter("http.connection.timeout", new Integer(5000))
        http.getClient().getParams().setParameter("http.socket.timeout", new Integer(5000))

        try {
            http.request(Method.POST, ContentType.JSON) { req ->
                body = data

                //headers.put(getOption('headerParamKey1', null), getOption('headerParamVal1', null))
                headers.Accept = 'application/json'

                response.success = { resp, json ->
                    logger.quiet 'Build stats reported'
                }
                response.failure = { resp ->
                    logger.quiet 'Failed to report build stats!'
                }
            }
        } catch (Exception exception) {
            logger.quiet sprintf('Failed to report build stats! %1$s', exception.toString())
        }
    }
}