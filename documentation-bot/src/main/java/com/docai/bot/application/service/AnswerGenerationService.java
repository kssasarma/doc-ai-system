package com.docai.bot.application.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.docai.bot.domain.model.RetrievedChunk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerGenerationService {

    private final ChatClient.Builder chatClientBuilder;

    public String generateAnswer(String question, String chatContext, 
                                 List<RetrievedChunk> relevantChunks) {
        
        // Check if no chunks were retrieved
        if (relevantChunks == null || relevantChunks.isEmpty()) {
            log.warn("No relevant chunks found for question: {}", question);
            return "We couldn't find the relative information in the documentation. " +
                   "Please try rephrasing your question or ensure you've specified the correct product and version.";
        }
        
        // Build the prompt with retrieved context
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a helpful documentation assistant. ");
        prompt.append("Answer the user's question based on the provided documentation excerpts.\n\n");
        
        if (chatContext != null && !chatContext.isEmpty()) {
            prompt.append("Previous conversation context:\n");
            prompt.append(chatContext);
            prompt.append("\n\n");
        }
        
        prompt.append("Relevant documentation excerpts:\n");
        for (int i = 0; i < relevantChunks.size(); i++) {
            prompt.append(String.format("[Source %d] %s\n", 
                i + 1, relevantChunks.get(i).getContent()));
        }
        
        prompt.append("\n\nUser question: ");
        prompt.append(question);
        prompt.append("\n\nProvide a clear, accurate answer based on the documentation excerpts. ");
        prompt.append("If the documentation doesn't contain relevant information, say so.");
        
        try {
            // Build chat client - will use the configured model from application.yml
            ChatClient chatClient = chatClientBuilder.build();
            String answer = chatClient.prompt()
                .user(prompt.toString())
                .call()
                .content();
            
            log.info("Generated answer for question: {}", 
                     question.substring(0, Math.min(50, question.length())));
            
            return answer;
        } catch (Exception e) {
            log.error("Failed to generate answer", e);
            return "I apologize, but I encountered an error generating an answer. Please try again.";
        }
    }
}
