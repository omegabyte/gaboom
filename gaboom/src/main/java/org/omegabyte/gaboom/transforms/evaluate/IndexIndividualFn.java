package org.omegabyte.gaboom.transforms.evaluate;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.values.KV;
import org.omegabyte.gaboom.Individual;

import java.io.Serializable;

public class IndexIndividualFn<GenomeT extends Serializable> extends DoFn<Individual<GenomeT>, KV<String, Individual<GenomeT>>> {
    @ProcessElement
    public void processElement(ProcessContext c) {
        Individual<GenomeT> individual = c.element();
        c.output(KV.of(individual.getId(), individual));
    }
}