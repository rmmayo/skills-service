/*
 * Copyright 2025 SkillTree
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
export const useInstructionGenerator = () => {

    const newDescriptionInstructions = (userEnteredText) => {
        return `Generate a detailed description for a skill that will be part of a larger training. Here is user provided text that gives information about the skills: "${userEnteredText}". 

Here are the requirements:
- Do not provide an introduction. 
- Use Markdown. 
- Use the word "skill" instead of "training". 
- Do not include the word "skill" in any titles.
- Do not wrap sections with \`\`\`
`
    }

    const existingDescriptionInstructions = (existingDescription, userEnteredText, instructionsToKeepPlaceholders) => {
        return `Here is the current description:
"${existingDescription}"

Please modify it based on the following instructions: "${userEnteredText}"

Here are the specific requirements:
- First provide the new text without any comments or fields (such as "corrected text")
- At the end create a new section with the title of "Here is what was changed" - then list any comments or suggestions.
${instructionsToKeepPlaceholders ? `-${instructionsToKeepPlaceholders}` : ''}
`
    }

    const quizRules = `
# Quiz Generation Instructions

## Output Format
Return a JSON object with exactly two properties:
1. \`numQuestions\`: Number of questions generated
2. \`questions\`: Array of question objects

## Question Requirements
### 1. SingleChoice Questions
- **Requirements**:
  - Exactly 1 correct answer
  - 2-3 incorrect answers
  - Mark correct answer with \`"isCorrect": true\`
  - All other answers must have \`"isCorrect": false\`

### 2. MultipleChoice Questions
- **Requirements**:
  - Minimum 2 correct answers
  - 3-5 total options
  - All correct answers must have \`"isCorrect": true\`
  - All incorrect answers must have \`"isCorrect": false\`

### 3. TextInput Questions
- **Requirements**:
  - Always return empty answers array: \`"answers": []\`
  - No need to provide answer options

## Content Guidelines
- Include at least one question of each type
- Ensure questions are clear, concise, and appropriate difficulty
- Make incorrect answers plausible but clearly wrong
- Avoid:
  - Trick questions
  - Ambiguous phrasing
  - Questions that are too easy/hard
  - Questions that are too similar to each other

## Technical Specifications
- Format: Strict JSON
- Each question must include:
  \`\`\`json
  {
    "quizId": "string",  // Same for all questions
    "question": "string", // Markdown supported
    "questionType": "SingleChoice|MultipleChoice|TextInput",
    "answers": [
      {
        "answer": "string",
        "isCorrect": boolean
      }
    ]
  }    
    `

    const newQuizInstructions = (existingDescription, numQuestions) => {
        return `
# Task: Generate a quiz for a skill that will be part of a larger training. 

## Objective
Generate a quiz with ${numQuestions} questions for a skill that will be part of a larger training based on this description:
"${existingDescription}".

${quizRules}
`
    }

    const updateQuizInstructions = ( existingDescription, existingQuiz, userEnteredText, instructionsToKeepPlaceholders ) => {
        return `
# Task: Modify an existing quiz for a skill that will be part of a larger training. 

## Objective
Modify an existing quiz

The quiz was originally built based on this description:
"${existingDescription}".

Here is the existing quiz:
"${existingQuiz}" 

Please modify it based on the following instructions: "${userEnteredText}"

${quizRules}
`
    }

    const newQuestionInstructions = (userInput) => {
        return `# Task: Generate a multiple-choice question with answers based on the user's description.

## User's Request:
"${userInput}"

## Instructions:
1. First, generate a clear and concise question based on the user's description.
2. Then, provide 3-5 answer choices in JSON format.
3. Mark 1-3 answers as correct (must have at least 1 correct answer).
4. Ensure answers are plausible and relevant to the question.

## Required Response Format:
### Question:
[Your generated question here]

### Answers:
[Your JSON array of answers here]

## Example Response:
### Question:
What are some popular chess openings?

### Answers:
[
  {"answer": "The Ruy Lopez", "isCorrect": true},
  {"answer": "The Sicilian Defense", "isCorrect": true},
  {"answer": "The King's Gambit", "isCorrect": false},
  {"answer": "The Italian Game", "isCorrect": true},
  {"answer": "The Caro-Kann Defense", "isCorrect": false}
]

## Important Notes:
- Start with "### Question:" on its own line
- Follow with the question text
- Add a blank line
- Then "### Answers:" on its own line
- Follow with the JSON array
- The JSON must be valid and properly formatted
- Include explanations in the answers if the question is complex
- Do not include any other text outside these sections
- Do not number answers`
    }
    
  const generatedProjectInstructions = (userInput) => {
    return `
Using the knowledge store data (uploaded documents) and relying on the instructions provided for this GPT, please create a training curriculum for ${userInput}. Please provide enough details in the individual skill descriptions so that the students will be able to provided a detailed justification of what they've learned and be able to pass a quiz on the skills keys concepts. Please provide a link to download the resulting JSON file.

Instructions for this GPT:

This GPT is called Synergy SkillTree Curriculum Development GPT.
Its purpose is to ingest, analyze, and synthesize disparate knowledge sources (uploaded documents) into a focused, modular, and standards-aligned curriculum designed for integration into the SkillTree Gamified Training Platform. Consider SkillTreeConcepts.pdf to better understand SkillTree Platfrom. 

Purpose and Role

The GPT’s primary goal is to convert unstructured and heterogeneous data (Word docs, Google docs, spreadsheets, PDFs, text files, etc.) into a coherent, validated training curriculum that can be translated into SkillTree subjects, skills, badges, etc..

The GPT supports its SkillTree users:

Government program managers and analysts, who will review and validate curriculum content for accuracy, policy alignment, and measurable outcomes.

Key Behaviors

Data-grounded: The GPT ideates almost exclusively from materials stored in the data/knowledge store.

Transparent inference: When the GPT must infer, synthesize, or extrapolate beyond the data, it must clearly flag these instances with the following format:

Traceability: Each curriculum element (skill name, skill description) should include source references or summaries of where in the data those concepts originated.

Curriculum alignment: Organize learning content using recognized frameworks such as:

Bloom’s Taxonomy

Competency-based progression

Modular/branch-based skill progression (aligned with SkillTree structure)

Collaborative validation: The GPT should highlight points that need review, feedback, or external confirmation by human analysts.

How this GPT should respond

Responses should be:

Structured and scannable: Detailed descriptions that use Markdown with sections, headers, tables, blocks and consistent formatting.

Professional and technical: Clear, objective, and free of unnecessary prose.

Action-oriented: Every response should produce directly usable material for SkillTree configuration or analyst review.

Collaborative: Prompt the user to verify assumptions, validate data alignment, or approve structure before finalization.

Output Details 

- Descriptions must be detailed and must use markdown formatting
  - Use headers, lists, tables, code blocks
- Between 3 and 6 subjects
  - Each subject will have a description 
  - Each subject will have between 10 and 20 skills
  - icon: icon css class from FontAwesomeFree library
- Each skill will have the following attributes
  - name: name of the skill
  - description: ** Very Important** - Skill Descriptions must be detailed and must use markdown formatting, as they are the content and heart of the learning material for students.  The skill descriptions should be a detailed explanation, describing the skill and how a skill should be achieved; use markdown to produce rich and clear skill description.  
  - skillId: unique identifier; be english characters only; no numbers of special characters
  - icon: icon css class from FontAwesomeFree library
  - pointIncrement: Number of points added for each skill event
  - numOccurrencesToCompletion: Number of successful occurrences to fully accomplish this skill; used in conjunction with the 'Point Increment' property; default to 1 when not sure 
  - selfReporting: select Approval or HonorSystem, do not select Quiz or Video
- Between 2 and 4 badges
  - each badge should have between 5 and 10 skills
  - icon: icon css class from FontAwesomeFree library


GPT Output Goal:
A structured export-ready schema (JSON) with fields like:

project.name
project.description
project.id
subject.name
subject.id
subject.description
skill.name
skill.id
skill.description

{
  "project": {
    "id": "projId",
    "name": "name goes here",
    "description": "detailed descripton"
  },
  "subjects": [
    {
      "id": "subjectId",
      "name": "Subject Name",
      "description": "subject description",
     "icon": "fa-solid fa-building",
      "skills": [
        {"name": "skill name", "skillId": "skillId", "icon": "fa-building", "pointIncrement": 10, "numOccurrencesToCompletion": 1, "selfReporting": "HonorSystem"},
        {"name": "skill name", "skillId": "skillId", "icon": "fa-building", "pointIncrement": 10, "numOccurrencesToCompletion": 1, "selfReporting": "HonorSystem"},
      ]
    },
     {
      "id": "subjectId",
      "name": "Subject Name",
      "description": "subject description",
      "icon": "fa-solid fa-building",
      "skills": [
        {"name": "skill name", "skillId": "skillId", "icon": "fa-building", "pointIncrement": 10, "numOccurrencesToCompletion": 1, "selfReporting": "HonorSystem"},
        {"name": "skill name", "skillId": "skillId", "icon": "fa-building", "pointIncrement": 10, "numOccurrencesToCompletion": 1, "selfReporting": "HonorSystem"},
      ]
    },
  ],
  "badges": [
    {
      "id": "badgeId",
      "name": "Badge Name",
      "description": "badge description",
      "icon": "fa-solid fa-building",
      "skillIds": [ "skillId1", "skillId2"]
    },
 }
    `
    }
    return {
        newDescriptionInstructions,
        existingDescriptionInstructions,
        newQuizInstructions,
        updateQuizInstructions,
        newQuestionInstructions,
        generatedProjectInstructions
    }
}