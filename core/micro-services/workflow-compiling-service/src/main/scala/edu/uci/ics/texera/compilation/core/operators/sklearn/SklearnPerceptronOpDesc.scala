package edu.uci.ics.texera.compilation.core.operators.sklearn

class SklearnPerceptronOpDesc extends SklearnMLOpDesc {
  override def getImportStatements = "from sklearn.linear_model import Perceptron"
  override def getUserFriendlyModelName = "Linear Perceptron"
}
