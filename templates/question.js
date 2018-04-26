var form = $('#answerForm');
form.submit(function() {
    var answer = normalize($('#answer').val());
    var questionHash = form.data('question-hash');
    var hash = CryptoJS.MD5(questionHash + "|" + answer);
    var checksum = hash.toString(CryptoJS.enc.Base64);
    console.log("checksum",checksum);
    var url = checksum+".html";

    $.ajax({
        type: 'HEAD',
        url: url,
        success: function(){
            window.location.href = url;
        },
        error: function() {
            form.replaceWith('<div class="alert alert-danger"><strong>Nope!</strong> Please refresh and try again </div>');
        }
      });

    return false;
});

function normalize(text) {
    return text.trim().toUpperCase().replace(/\s\s+/g, ' ');
}