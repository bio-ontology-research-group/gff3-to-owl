import org.semanticweb.owlapi.io.* 
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary

def onturi = "http://phenomebrowser.net/gff3/#"

def infile = new File(args[0])
def outfile = args[1]

def id2class = [:] // maps an OBO-ID to an OWLClass

OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
OWLDataFactory factory = manager.getOWLDataFactory()

def ontSet = new TreeSet()
//ontSet.add(manager.loadOntologyFromOntologyDocument(new File("obo/so.obo")))
ontSet.add(manager.loadOntologyFromOntologyDocument(new File("obo/so-xp.obo")))
ontSet.add(manager.loadOntologyFromOntologyDocument(new File("obo/gene_ontology_edit.obo")))

def somap = [:]

new File("obo/sequence.tbl").splitEachLine("\t") { line ->
  def id = line[0]
  def name = line[1].toLowerCase()
  somap[name] = id
}

OWLOntology ontology = manager.createOntology(IRI.create(onturi), ontSet)

ontology.getClassesInSignature(true).each {
  def a = it.toString()
  a = a.substring(a.indexOf("obo/")+4,a.length()-1)
  a = a.substring(a.indexOf('#')+1)
  a = a.replaceAll("_",":")
  if (id2class[a] == null) {
    id2class[a] = it
  }
}

def addAnno = {resource, prop, cont ->
  OWLAnnotation anno = factory.getOWLAnnotation(
    factory.getOWLAnnotationProperty(prop.getIRI()),
    factory.getOWLTypedLiteral(cont))
  def axiom = factory.getOWLAnnotationAssertionAxiom(resource.getIRI(),
						     anno)
  manager.addAxiom(ontology,axiom)
}

def r = { String s ->
  factory.getOWLObjectProperty(IRI.create("http://bioonto.de/ro2.owl#"+s))
}

def c = { String s ->
  s = s.replaceAll("\\(","").replaceAll("\\)","")
  factory.getOWLClass(IRI.create(onturi+s))
}
def i = { String s ->
  s = s.replaceAll("\\(","").replaceAll("\\)","")
  factory.getOWLNamedIndividual(IRI.create(onturi+s))
}

def a = { String s ->
  factory.getOWLAnnotationProperty(IRI.create("http://bioonto.de/ro2.owl#"+s))
}


def counter = 0
OWLAxiom ax = null
infile.splitEachLine("\t") { line ->
  if (!line[0].startsWith('#')) {
    def id = line[0]
    def soterm = line[2]
    if (soterm != null) {
      def soid = soterm
      if (somap[soterm.toLowerCase()]!=null) {
	soid=somap[soterm.toLowerCase()]
      }
      def soclass = id2class[soid]
      if (soclass == null) {
	soclass = factory.getOWLThing()
      }
      def start = line[3]
      def end = line[4]

      def cl = null

      def attributes = line[8]
      attributes.split(";").each { attr ->
	attr = URLDecoder.decode(attr)
	if (attr.toLowerCase().startsWith("id=")) {
	  def desc = attr.substring(3)
	  cl = i(desc)
	}
      }

      if (cl == null) {
	def desc = counter+""
	cl = i("anon-"+desc)
	counter += 1
      }
      ax = factory.getOWLClassAssertionAxiom(soclass, cl)
      manager.addAxiom(ontology, ax)

      addAnno(cl, a("start"), start)
      addAnno(cl, a("end"), end)
      addAnno(cl, a("source"), line[1])
      addAnno(cl, a("score"), line[5])
      addAnno(cl, a("strand"), line[6])
      addAnno(cl, a("phase"), line[7])

      attributes.split(";").each { attr ->
	attr = URLDecoder.decode(attr)
	if (attr.startsWith("Parent=")) {
	  def par = i(attr.substring(7))
	  ax = factory.getOWLObjectPropertyAssertionAxiom(r("part-of"), cl, par)
	  manager.addAxiom(ontology, ax)
	  ax = factory.getOWLObjectPropertyAssertionAxiom(r("has-part"), cl, par)
	  manager.addAxiom(ontology, ax)
	}
	if (attr.toLowerCase().startsWith("description=")) {
	  def desc = attr.substring(12)
	  addAnno(cl, OWLRDFVocabulary.RDF_DESCRIPTION, desc)
	}
	if (attr.toLowerCase().startsWith("node=")) {
	  def desc = attr.substring(5)
	  addAnno(cl, OWLRDFVocabulary.RDF_DESCRIPTION, desc)
	}
	if (attr.toLowerCase().startsWith("name=")) {
	  def desc = attr.substring(5)
	  addAnno(cl, OWLRDFVocabulary.RDFS_LABEL, desc)
	}
	if (attr.toLowerCase().startsWith("alias=")) {
	  def desc = attr.substring(6)
	  addAnno(cl, OWLRDFVocabulary.RDFS_LABEL, desc)
	}
	if (attr.toLowerCase().startsWith("dbxref=")) {
	  def desc = attr.substring(7)
	  addAnno(cl, a("dbxref"), desc)
	}
	if (attr.toLowerCase().startsWith("ontology_term")) {
	  def term = attr.substring(13)
	  term.split(",").each { t ->
	    if (id2class[t]!=null) {
	      ax = factory.getOWLClassAssertionAxiom(factory.getOWLObjectSomeValuesFrom(r("has-annotation"), id2class[t]), cl)
	      manager.addAxiom(ontology, ax)
	    }
	  }
	}
      }
    }
  }
}

manager.saveOntology(ontology, IRI.create("file:"+outfile))

