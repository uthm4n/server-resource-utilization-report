

import com.morpheusdata.uthman.ServerResourceUtilReportPlugin
import com.morpheusdata.uthman.ServerResourceUtilReportProvider
import com.morpheusdata.uthman.util.ElasticsearchUtils
import com.morpheusdata.core.MorpheusContext
import spock.lang.Specification
import spock.lang.Subject

class ElasticTestSpec extends Specification {
    @Subject
    ServerResourceUtilReportProvider reportProvider

    MorpheusContext context
    ServerResourceUtilReportPlugin plugin
    ElasticsearchUtils elasticUtils

    void setup() {
        context = Mock(MorpheusContext)
        plugin = Mock(ServerResourceUtilReportPlugin)
        elasticUtils = Mock(ElasticsearchUtils)
        reportProvider = new ServerResourceUtilReportProvider(plugin, context)


    }

    void "getServiceProperties"() {
        given:
        def services = reportProvider.getProperties()
        when:
        services.properties.size() > 1
        then:
        println """ ${services.properties} """
    }

    void "testElasticQuery"() {
        given:
        def elasticQuery = ElasticsearchUtils.getQuery(true)

        when:
        elasticQuery.query.bool.filter.range.ts = "now-30d/d"

        then:
        def results = ElasticsearchUtils.executeQuery(elasticQuery)
        println """
    
                                            [ DEBUG ELASTIC RESPONSE ]
                    -----------------------------------------------------------
                      query:   ${elasticQuery}
                       data:   ${results}
    
                      props:   ${results.properties}
    
    
                    """
    }

    void "getDebugInfo"() {

        when:
        reportProvider

        then:
        println """
                                        [ DEBUG ]
                  -----------------------------------------------------------
 
               class:    ${reportProvider.class}

               name:    ${reportProvider.name}
           
    morpheusContext:    ${this.context.properties}
           morpheus:    ${reportProvider.morpheus}
           
              props:    ${reportProvider.properties}

"""

    }
}
