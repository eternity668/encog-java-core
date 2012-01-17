/*
 * Encog(tm) Core v3.1 - Java Version
 * http://www.heatonresearch.com/encog/
 * http://code.google.com/p/encog-java/
 
 * Copyright 2008-2012 Heaton Research, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *   
 * For more information on Heaton Research copyrights, licenses 
 * and trademarks visit:
 * http://www.heatonresearch.com/copyright
 */
package org.encog.ml.hmm;

import java.io.Serializable;
import java.util.Iterator;

import org.encog.ml.MLStateSequence;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.hmm.alog.ForwardBackwardCalculator;
import org.encog.ml.hmm.alog.ForwardBackwardScaledCalculator;
import org.encog.ml.hmm.alog.ViterbiCalculator;
import org.encog.ml.hmm.distributions.ContinousDistribution;
import org.encog.ml.hmm.distributions.DiscreteDistribution;
import org.encog.ml.hmm.distributions.StateDistribution;

/**
 * A Hidden Markov Model (HMM) is a Machine Learning Method that allows for
 * predictions to be made about the hidden states and observations of a given
 * system over time. A HMM can be thought of as a simple dynamic Bayesian
 * network. The HMM is dynamic as it deals with changes that unfold over time.
 * 
 * The Hidden Markov Model is made up of a number of states and observations. A
 * simple example might be the state of the economy. There are three hidden
 * states, such as bull market, bear market and level. We do not know which
 * state we are currently in. However, there are observations that can be made
 * such as interest rate and the level of the S&P500. The HMM learns what state
 * we are in by seeing how the observations change over time.
 * 
 * The HMM is only in one state at a given time. There is a percent probability
 * that the HMM will move from one state to any of the other states. These
 * probabilities are arranged in a grid, and are called the state transition
 * probabilities.
 * 
 * Observations can be discrete or continuous. These observations allow the HMM
 * to predict state transitions.
 * 
 * The HMM can handle single-value or multivariate observations.
 * 
 * http://www.heatonresearch.com/wiki/Hidden_Markov_Model
 * 
 * Rabiner, Juang, An introduction to Hidden Markov Models, IEEE ASSP Mag.,pp
 * 4-16, June 1986.
 * 
 * Baum, L. E.; Petrie, T. (1966).
 * "Statistical Inference for Probabilistic Functions of Finite State Markov Chains"
 * The Annals of Mathematical Statistics 37 (6): 1554-1563.
 * 
 */
public class HiddenMarkovModel implements MLStateSequence, Serializable,
		Cloneable {
	/**
	 * The serial id.
	 */
	private static final long serialVersionUID = 1L;
	
	/**
	 * The initial probabilities for each state.
	 */
	private double pi[];
	
	/**
	 * The transitional probabilities between the states.
	 */
	private double transitionProbability[][];
	
	/**
	 * The mapping of observation probabilities to the
	 * states.
	 */
	private final StateDistribution[] stateDistributions;
	private final int items;

	/**
	 * Construct a continuous HMM with the specified number of states.
	 * @param nbStates The number of states.
	 */
	public HiddenMarkovModel(final int nbStates) {
		this.items = -1;
		this.pi = new double[nbStates];
		this.transitionProbability = new double[nbStates][nbStates];
		this.stateDistributions = new StateDistribution[nbStates];

		for (int i = 0; i < nbStates; i++) {
			this.pi[i] = 1. / nbStates;

			if (isContinuous()) {
				this.stateDistributions[i] = new ContinousDistribution(
						getStateCount());
			} else {
				this.stateDistributions[i] = new DiscreteDistribution(
						getStateCount());
			}

			for (int j = 0; j < nbStates; j++) {
				this.transitionProbability[i][j] = 1. / nbStates;
			}
		}
	}

	public HiddenMarkovModel(final int theStates, final int theItems) {
		this.items = theItems;
		this.pi = new double[theStates];
		this.transitionProbability = new double[theStates][theStates];
		this.stateDistributions = new StateDistribution[theStates];

		for (int i = 0; i < theStates; i++) {
			this.pi[i] = 1. / theStates;
			this.stateDistributions[i] = new DiscreteDistribution(this.items);

			for (int j = 0; j < theStates; j++) {
				this.transitionProbability[i][j] = 1.0 / theStates;
			}
		}
	}

	@Override
	public HiddenMarkovModel clone() throws CloneNotSupportedException {
		final HiddenMarkovModel hmm = cloneStructure();

		hmm.pi = this.pi.clone();
		hmm.transitionProbability = this.transitionProbability.clone();

		for (int i = 0; i < this.transitionProbability.length; i++) {
			hmm.transitionProbability[i] = this.transitionProbability[i]
					.clone();
		}

		for (int i = 0; i < hmm.stateDistributions.length; i++) {
			hmm.stateDistributions[i] = this.stateDistributions[i].clone();
		}

		return hmm;
	}

	public HiddenMarkovModel cloneStructure() {
		HiddenMarkovModel hmm;

		if (isDiscrete()) {
			hmm = new HiddenMarkovModel(getStateCount(), this.items);
		} else {
			hmm = new HiddenMarkovModel(getStateCount());
		}

		return hmm;
	}

	public StateDistribution createNewDistribution() {
		if (isContinuous()) {
			return new ContinousDistribution(this.items);
		} else {
			return new DiscreteDistribution(this.items);
		}
	}

	public double getPi(final int stateNb) {
		return this.pi[stateNb];
	}

	public int getStateCount() {
		return this.pi.length;
	}

	public StateDistribution getStateDistribution(final int i) {
		return this.stateDistributions[i];
	}

	@Override
	public int[] getStatesForSequence(final MLDataSet seq) {
		return (new ViterbiCalculator(seq, this)).stateSequence();
	}

	public double getTransitionProbability(final int i, final int j) {
		return this.transitionProbability[i][j];
	}

	public boolean isContinuous() {
		return this.items == -1;
	}

	public boolean isDiscrete() {
		return !isContinuous();
	}

	public double lnProbability(final MLDataSet seq) {
		return (new ForwardBackwardScaledCalculator(seq, this)).lnProbability();
	}

	@Override
	public double probability(final MLDataSet seq) {
		return (new ForwardBackwardCalculator(seq, this)).probability();
	}

	@Override
	public double probability(final MLDataSet seq, final int[] states) {
		if ((seq.size() != states.length) || (seq.size() < 1)) {
			throw new IllegalArgumentException();
		}

		double probability = getPi(states[0]);

		final Iterator<MLDataPair> oseqIterator = seq.iterator();

		for (int i = 0; i < (states.length - 1); i++) {
			probability *= getStateDistribution(states[i]).probability(
					oseqIterator.next())
					* getTransitionProbability(states[i], states[i + 1]);
		}

		return probability
				* getStateDistribution(states[states.length - 1]).probability(
						seq.get(states.length - 1));
	}

	public void setPi(final int stateNb, final double value) {
		this.pi[stateNb] = value;
	}

	public void setStateDistribution(final int stateNb,
			final StateDistribution opdf) {
		this.stateDistributions[stateNb] = opdf;
	}

	public void setTransitionProbability(final int i, final int j,
			final double value) {
		this.transitionProbability[i][j] = value;
	}
}
