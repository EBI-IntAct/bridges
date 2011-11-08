package uk.ac.ebi.intact.bridges.ontology_manager.impl.local;

import psidev.psi.tools.ontology_manager.impl.local.AbstractLocalOntology;
import psidev.psi.tools.ontology_manager.impl.local.OntologyLoaderException;
import psidev.psi.tools.ontology_manager.interfaces.OntologyAccessTemplate;
import uk.ac.ebi.intact.bridges.ontology_manager.builders.IntactOntologyTermBuilder;
import uk.ac.ebi.intact.bridges.ontology_manager.interfaces.IntactOntologyTermI;

import java.io.File;

/**
 * Intact implementation of LocalOntology access
 *
 * @author Marine Dumousseau (marine@ebi.ac.uk)
 * @version $Id$
 * @since <pre>08/11/11</pre>
 */

public class IntactLocalOntology extends AbstractLocalOntology<IntactOntologyTermI, IntactOntology, IntactOboLoader> implements OntologyAccessTemplate<IntactOntologyTermI> {

    protected IntactOntologyTermBuilder termBuilder;

    public IntactLocalOntology(IntactOntologyTermBuilder termBuilder){
        super();
        if (termBuilder == null){
            throw new IllegalArgumentException("The IntactOntologyTerm builder must be non null");
        }
        this.termBuilder = termBuilder;
    }
    @Override
    protected IntactOboLoader createNewOBOLoader(File ontologyDirectory) throws OntologyLoaderException {
        return this.termBuilder.createIntactOboLoader(ontologyDirectory);
    }
}
