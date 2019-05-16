package co.droidchef.android.buildtimetracker.reporters

import co.droidchef.android.buildtimetracker.Timing
import groovyx.net.http.HTTPBuilder
import net.sf.json.JSON

import java.text.DateFormat
import java.text.SimpleDateFormat
import org.gradle.api.logging.Logger

class WarehouseReporter extends AbstractBuildTimeTrackerReporter {
    WarehouseReporter(HashMap<String, String> options, Logger logger) {
        super(options, logger)
    }

    List measurements(List<Timing> timings) {
        long timestamp = new TrueTimeProvider().getCurrentTime()

        TimeZone tz = TimeZone.getTimeZone("UTC")
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'")
        df.setTimeZone(tz)

        def hostname = "unknown"
        try {
            InetAddress localhost = InetAddress.getLocalHost()
            hostname = localhost.getHostName()
        } catch (UnknownHostException exception) {
            // do nothing
        }

        def info = new SysInfo()
        def osId = info.getOSIdentifier()
        def cpuId = info.getCPUIdentifier()
        def maxMem = info.getMaxMemory()
        def measurements = []
        def ultimateSuccess = timings.every { it.success }
        def userName = System.getProperty("user.name");
        timings.eachWithIndex { it, index ->
            measurements << [
                    timestamp       : timestamp,
                    order           : index,
                    task            : it.path,
                    success         : it.success,
                    did_work        : it.didWork,
                    skipped         : it.skipped,
                    ms              : it.ms,
                    cpu             : cpuId,
                    memory          : maxMem,
                    os              : osId,
                    hostname        : hostname,
                    git_sha         : getOption('git', ''),
                    ultimate_success: ultimateSuccess,
                    user_name       : userName
            ]
        }

        return measurements
    }

    @Override
    def run(List<Timing> timings) {
        def measurements = measurements(timings)

        // write to server
        def url = getOption("url", null)
        def http = new HTTPBuilder(url)
        http.getClient().getParams().setParameter("http.connection.timeout", new Integer(5000))
        http.getClient().getParams().setParameter("http.socket.timeout", new Integer(5000))

        try {
            http.request(POST, JSON) { req ->
                body = measurements

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