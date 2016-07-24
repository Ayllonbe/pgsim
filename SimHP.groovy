@Grab(group='com.github.sharispe', module='slib-sml', version='0.9.1')
@Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0')

import java.net.*
import org.openrdf.model.vocabulary.*
import slib.sglib.io.loader.*
import slib.sml.sm.core.metrics.ic.utils.*
import slib.sml.sm.core.utils.*
import slib.sglib.io.loader.bio.obo.*
import org.openrdf.model.URI
import slib.graph.algo.extraction.rvf.instances.*
import slib.sglib.algo.graph.utils.*
import slib.utils.impl.Timer
import slib.graph.algo.extraction.utils.*
import slib.graph.model.graph.*
import slib.graph.model.repo.*
import slib.graph.model.impl.graph.memory.*
import slib.sml.sm.core.engine.*
import slib.graph.io.conf.*
import slib.graph.model.impl.graph.elements.*
import slib.graph.algo.extraction.rvf.instances.impl.*
import slib.graph.model.impl.repo.*
import slib.graph.io.util.*
import slib.graph.io.loader.*
import groovyx.gpars.GParsPool



def factory = URIFactoryMemory.getSingleton()

class Gene {

  int id
  Set annotations

  public Gene(id, annotations) {
    setId(id)
    setAnnotations(annotations)
  }

  void addAnnotation(annotation) {
    annotations.add(annotation);
  }

  def getAnnotations() {
    annotations
  }

}


def getHPOntology = {

  // Load OBO file to graph "go.obo"
  String goOBO = "data/hp.obo";
  // String annot = "data/gene_association.goa_uniprot.txt";

  URI graph_uri = factory.getURI("http://hp/");

  factory.loadNamespacePrefix("HP", graph_uri.toString());

  GraphConf graphConf = new GraphConf(graph_uri);
  graphConf.addGDataConf(new GDataConf(GFormat.OBO, goOBO));
  // graphConf.addGDataConf(new GDataConf(GFormat.GAF2, annot));

  G graph = GraphLoaderGeneric.load(graphConf);
  URI virtualRoot = factory.getURI("http://hp/virtualRoot")
  graph.addV(virtualRoot)
  GAction rooting = new GAction(GActionType.REROOTING)
  rooting.addParameter("root_uri", virtualRoot.stringValue())
  GraphActionExecutor.applyAction(factory, rooting, graph)

  return graph
}

def getGenes = {
  def genes = []
  def i = 0
  new File("data/hp_annotations.txt").splitEachLine('\t') { items ->
    def s = 0
    genes.push(new Gene(i, new LinkedHashSet()))
    for (item in items) {
      genes[i].addAnnotation(factory.getURI(item))
    }
    i++
  }
  return genes
}

graph = getHPOntology()
genes = getGenes()

def sim_id = this.args[0].toInteger()

SM_Engine engine = new SM_Engine(graph)


// DAG-GIC, DAG-NTO, DAG-UI

String[] flags = [
  SMConstants.FLAG_SIM_GROUPWISE_DAG_GIC,
  SMConstants.FLAG_SIM_GROUPWISE_DAG_NTO,
  SMConstants.FLAG_SIM_GROUPWISE_DAG_UI
]

// All
// List<String> flags = new ArrayList<String>(SMConstants.SIM_GROUPWISE_DAG.keySet());
// System.out.println(flags.size());
ICconf icConf = new IC_Conf_Topo("Sanchez", SMConstants.FLAG_ICI_SANCHEZ_2011);
// ICconf icConf = new IC_Conf_Corpus(SMConstants.FLAG_IC_ANNOT_RESNIK_1995);

// Map<URI, Double> ics = engine.getIC_results(icConf);
// for(URI uri: ics.keySet()) {
//   println uri.toString() + " " + ics.get(uri)
// }


String flagGroupwise = flags[sim_id];
SMconf smConf = new SMconf(flagGroupwise);
smConf.setICconf(icConf);

def result = new Double[genes.size() * genes.size()]
for (i = 0; i < result.size(); i++) {
  result[i] = i
}

def c = 0

GParsPool.withPool {
  result.eachParallel { val ->
    def i = val.toInteger()
    def x = i.intdiv(genes.size())
    def y = i % genes.size()
    if (x <= y) {
      result[i] = engine.compare(
              smConf,
              genes[x].getAnnotations(),
              genes[y].getAnnotations())
      if (c % 100000 == 0)
        println c
      c++
    }
  }
}

def fout = new PrintWriter(new BufferedWriter(
  new FileWriter("data/groupwise_hp/" + flagGroupwise + ".txt")))
for (i = 0; i < result.size(); i++) {
  def x = i.intdiv(genes.size())
  def y = i % genes.size()
  if (x <= y) {
    fout.println(result[i])
  } else {
    def j = y * genes.size() + x
    fout.println(result[j])
  }
}
fout.flush()
fout.close()
