package co.droidchef.android.buildtimetracker

import co.droidchef.android.buildtimetracker.reporters.AbstractBuildTimeTrackerReporter
import co.droidchef.android.buildtimetracker.reporters.CSVSummaryReporter
import co.droidchef.android.buildtimetracker.reporters.JSONReporter
import co.droidchef.android.buildtimetracker.reporters.SummaryReporter
import co.droidchef.android.buildtimetracker.reporters.CSVReporter
import co.droidchef.android.buildtimetracker.reporters.WarehouseReporter
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

class BuildTimeTrackerPlugin implements Plugin<Project> {
    def REPORTERS = [
        summary: SummaryReporter,
        csv: CSVReporter,
        csvSummary: CSVSummaryReporter,
        json: JSONReporter,
        warehouse: WarehouseReporter
    ]
    Logger logger

    NamedDomainObjectCollection<ReporterExtension> reporterExtensions

    @Override
    void apply(Project project) {
        this.logger = project.logger
        project.extensions.create("buildtimetracker", BuildTimeTrackerExtension)
        reporterExtensions = project.buildtimetracker.extensions.reporters = project.container(ReporterExtension)
        project.gradle.addBuildListener(new TimingRecorder(this))
    }

    List<AbstractBuildTimeTrackerReporter> getReporters() {
        reporterExtensions.collect { ext ->
            if (REPORTERS.containsKey(ext.name)) {
                return REPORTERS.get(ext.name).newInstance(ext.options, logger)
            }
        }.findAll { ext -> ext != null } as List<AbstractBuildTimeTrackerReporter>
    }
}

class BuildTimeTrackerExtension {
    // Not in use at the moment.
}

class ReporterExtension {
    final String name
    final Map<String, String> options = [:]

    ReporterExtension(String name) {
        this.name = name
    }

    @Override
    String toString() {
        return name
    }

    def methodMissing(String name, args) {
        // I'm feeling really, really naughty.
        if (args.length == 1) {
            options[name] = args[0].toString()
        } else {
            throw new MissingMethodException(name, this.class, args)
        }
    }
}
