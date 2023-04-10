package part1Recap

object ContextualAbstraction {
  case class Person (name: String, age: Int)

  trait JSONSerializer[T] {
    def toJSON(value: T): String
  }

  // step 2: type class instances
  given stringSerializer: JSONSerializer[String] with {
    override def toJSON(value: String): String = "\"" + value + "\""
  }
  given intSerializer: JSONSerializer[Int] with {
    override def toJSON(value: Int): String = value.toString
  }
  given personSerializer: JSONSerializer[Person] with {
    def toJSON(person: Person): String = 
      s"""
      |{"name": "${person.name}", "age": ${person.age}}
      |""".stripMargin
  }

  // step 3: user-facing API
  def convert2Json[T](value: T)(using serializer: JSONSerializer[T]): String = 
    serializer.toJSON(value)

  // more detailed
  def convertList2Json[T](list: List[T])(using serializer: JSONSerializer[T]): String =
    list.map(value => serializer.toJSON(value)).mkString("[", ",", "]")

  // step 4: extension method just for the types we support
  extension [T](value: T)
    def toJSON(using serializer: JSONSerializer[T]): String = 
      serializer.toJSON(value)
  
  def main(args: Array[String]): Unit = {
    println(convertList2Json(List(Person("Alice", 20)))) // dont need step 4
    println(Person("Bob", 24).toJSON) // need step 4
  }
}
